package com.focusforge.app.enforcement

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusforge.app.BlockedAppActivity
import com.focusforge.app.FocusForgeApp
import com.focusforge.app.MainActivity
import com.focusforge.app.R
import com.focusforge.app.data.model.BlockResponseMode
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.UpstreamResolverProvider
import com.focusforge.app.data.repository.PolicyStore
import com.focusforge.app.service.TimerService
import com.focusforge.app.service.TimerStatus
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.inject.Inject

/**
 * Local-only DNS VPN Service.
 *
 * Routes ONLY virtual DNS addresses (10.0.0.1/32 and fd00::1/128) into the TUN interface.
 * All general internet traffic bypasses the VPN and never enters FocusForge.
 *
 * Privacy: No queried or blocked domain name is ever written to disk or logs.
 * Safety: Fails OPEN on repeated upstream errors or missing network.
 */
@AndroidEntryPoint
class DnsVpnService : VpnService() {

    @Inject
    lateinit var policyStore: PolicyStore

    @Inject
    lateinit var blocklistEngine: BlocklistEngine

    @Inject
    lateinit var ruleChangeGate: RuleChangeGate

    @Inject
    lateinit var decisionBuffer: DnsDecisionBuffer

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val forwardExecutor = Executors.newFixedThreadPool(4)
    private val inflightSemaphore = Semaphore(MAX_CONCURRENT_QUERIES)
    private val healthMonitor = VpnHealthMonitor(errorThreshold = 5, windowMs = 15_000L)

    private var vpnInterface: ParcelFileDescriptor? = null
    private var vpnThread: Thread? = null
    @Volatile
    private var isRunning = false

    private var currentPolicy = PolicySnapshot()
    private var currentUpstreamProvider = UpstreamResolverProvider.SYSTEM_DEFAULT
    private var currentBlockMode = BlockResponseMode.NXDOMAIN
    private var excludeOwnAppFromVpn = true // Single clearly named setting: later needed for the in-app YouTube player, model downloads and sync

    @Volatile
    private var physicalNetwork: Network? = null
    @Volatile
    private var physicalLinkProps: LinkProperties? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isAirplaneReceiverRegistered = false
    private var autoResumeJob: Job? = null
    @Volatile
    private var lastBlockedSiteOverlayMillis = 0L

    private val airplaneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_AIRPLANE_MODE_CHANGED) {
                val isAirplaneOn = Settings.Global.getInt(
                    contentResolver,
                    Settings.Global.AIRPLANE_MODE_ON,
                    0
                ) != 0
                Log.i(TAG, "Airplane mode changed: on=$isAirplaneOn")
                if (isAirplaneOn) {
                    physicalNetwork = null
                    physicalLinkProps = null
                } else {
                    refreshPhysicalNetwork()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundWithNotification()
        registerPhysicalNetworkCallback()
        registerAirplaneReceiver()
        refreshPhysicalNetwork()

        serviceScope.launch {
            policyStore.policySnapshot.collect { snapshot ->
                val rulesChanged = currentPolicy.enabledTiers != snapshot.enabledTiers ||
                    currentPolicy.privateBlockedDomainHashes != snapshot.privateBlockedDomainHashes ||
                    currentPolicy.customAllowedDomainHashes != snapshot.customAllowedDomainHashes
                currentPolicy = snapshot
                val now = System.currentTimeMillis()
                if (snapshot.isPaused(now)) {
                    scheduleAutoResume(snapshot.pauseUntil!! - now)
                    if (isRunning) {
                        pauseTunnelOnly()
                    }
                } else if (!snapshot.isDnsBlockingEffective(now) && isRunning) {
                    Log.i(TAG, "No DNS filtering active in PolicySnapshot; stopping VPN")
                    stopVpn()
                } else if (snapshot.isDnsBlockingEffective(now) && !isRunning && policyStore.isDnsProtectionEnabled.value) {
                    startVpnTunnel()
                } else if (isRunning && rulesChanged) {
                    // Re-establish TUN so Android netd flushes cached DNS entries for newly added/removed rules
                    Log.i(TAG, "DNS policy rules updated; refreshing TUN to flush system DNS cache")
                    restartTunnel()
                }
            }
        }

        serviceScope.launch {
            policyStore.upstreamProvider.collect { currentUpstreamProvider = it }
        }
        serviceScope.launch {
            policyStore.blockResponseMode.collect { currentBlockMode = it }
        }
        serviceScope.launch {
            policyStore.excludeOwnAppFromVpn.collect { currentExclude ->
                val changed = excludeOwnAppFromVpn != currentExclude
                excludeOwnAppFromVpn = currentExclude
                if (changed && isRunning) {
                    restartTunnel()
                }
            }
        }
        serviceScope.launch {
            policyStore.todayBlockedCount.collect { count ->
                _todayBlockedCount.value = count
                if (isRunning) updateActiveNotification(count)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_VPN -> {
                stopVpn()
                return START_NOT_STICKY
            }
            ACTION_PAUSE_5M -> {
                serviceScope.launch {
                    ruleChangeGate.pauseProtectionFor5Minutes()
                }
                return START_STICKY
            }
            ACTION_RESUME_NOW -> {
                serviceScope.launch {
                    ruleChangeGate.resumeProtectionNow()
                    healthMonitor.reset()
                    _isFailOpenPaused.value = false
                    clearFailOpenAlertNotification()
                    startVpnTunnel()
                }
                return START_STICKY
            }
            else -> {
                healthMonitor.reset()
                _isFailOpenPaused.value = false
                clearFailOpenAlertNotification()
                startVpnTunnel()
                return START_STICKY
            }
        }
    }

    private fun scheduleAutoResume(delayMs: Long) {
        autoResumeJob?.cancel()
        if (delayMs <= 0L) return
        autoResumeJob = serviceScope.launch {
            delay(delayMs + 200L)
            ruleChangeGate.resumeProtectionNow()
            if (policyStore.isDnsProtectionEnabled.value) {
                startVpnTunnel()
            }
        }
    }

    private fun startVpnTunnel() {
        if (isRunning) return
        if (currentPolicy.isPaused()) return

        try {
            val builder = Builder()
                .setSession("FocusForge Local DNS")
                // IPv4 virtual DNS tunnel only
                .addAddress("10.0.0.2", 32)
                .addDnsServer("10.0.0.1")
                .addRoute("10.0.0.1", 32)
                // IPv6 virtual DNS tunnel only
                .addAddress("fd00::2", 128)
                .addDnsServer("fd00::1")
                .addRoute("fd00::1", 128)
                .setMtu(1500)
                .setBlocking(true)

            if (excludeOwnAppFromVpn) {
                try {
                    builder.addDisallowedApplication(packageName)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not exclude own package: ${e.message}")
                }
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VpnService.Builder.establish() returned null")
                triggerFailOpen("VPN interface unavailable")
                return
            }

            isRunning = true
            _isVpnRunning.value = true
            updateActiveNotification(_todayBlockedCount.value)

            vpnThread = Thread({ runVpnLoop() }, "FocusForge-DnsTun").apply { start() }
            Log.i(TAG, "FocusForge DNS VPN tunnel established")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN tunnel: ${e.message}", e)
            triggerFailOpen("Tunnel start error")
        }
    }

    private fun restartTunnel() {
        pauseTunnelOnly()
        startVpnTunnel()
    }

    private fun pauseTunnelOnly() {
        isRunning = false
        _isVpnRunning.value = false
        vpnThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
    }

    private fun runVpnLoop() {
        val pfd = vpnInterface ?: return
        val inputStream = FileInputStream(pfd.fileDescriptor)
        val outputStream = FileOutputStream(pfd.fileDescriptor)
        val packetBuffer = ByteArray(4096)

        try {
            while (isRunning && !Thread.currentThread().isInterrupted) {
                val bytesRead = inputStream.read(packetBuffer)
                if (bytesRead <= 0) continue

                val query = DnsPacketParser.parseDnsQuery(packetBuffer, bytesRead) ?: continue
                handleDnsQuery(packetBuffer, bytesRead, query, outputStream)
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "TUN read loop error: ${e.message}")
            }
        } finally {
            try { inputStream.close() } catch (_: Exception) {}
            try { outputStream.close() } catch (_: Exception) {}
        }
    }

    private fun handleDnsQuery(
        packet: ByteArray,
        length: Int,
        query: DnsQuery,
        tunOut: FileOutputStream
    ) {
        val upstreamHashes = currentUpstreamProvider.bootstrapHostnames
            .mapNotNull { DomainNormalizer.normalizeAndHash(it) }
            .toSet()

        val result = blocklistEngine.checkDomain(
            rawDomain = query.domain,
            snapshot = currentPolicy,
            configuredUpstreamHashes = upstreamHashes,
            allowlistOverridesAdult = false
        )

        if (result.isBlocked) {
            serviceScope.launch {
                policyStore.incrementTodayBlockedCount()
            }
            val isPrivate = result.reason == DecisionReason.PRIVATE_RULE
            decisionBuffer.record(
                rawDomain = query.domain,
                qType = query.qType,
                decision = DnsBufferDecision.BLOCKED,
                reason = result.matchedTier?.name ?: result.reason.name,
                isPrivateRule = isPrivate,
                upstreamLatencyMs = 0L
            )

            val blockedResp = when (currentBlockMode) {
                BlockResponseMode.NXDOMAIN -> DnsPacketParser.buildNxdomainResponse(packet, length, query)
                BlockResponseMode.ZERO_IP -> DnsPacketParser.buildZeroIpResponse(packet, length, query)
            }
            writeToTun(tunOut, blockedResp)

            // If Focus Timer is actively running and overlay permission is granted,
            // show the full-screen "Time to Focus" window (5s cooldown, never passes domain name).
            if (TimerService.timerState.value.status == TimerStatus.RUNNING &&
                Settings.canDrawOverlays(this)
            ) {
                val now = System.currentTimeMillis()
                if (now - lastBlockedSiteOverlayMillis > 5_000L) {
                    lastBlockedSiteOverlayMillis = now
                    val focusIntent = Intent(this, BlockedAppActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(BlockedAppActivity.EXTRA_IS_SITE_BLOCKED, true)
                        putExtra(BlockedAppActivity.EXTRA_IS_PRIVATE_RULE, isPrivate)
                    }
                    try {
                        startActivity(focusIntent)
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // Bounded concurrency for upstream forwarding
        if (!inflightSemaphore.tryAcquire()) {
            val servfail = DnsPacketParser.buildServfailResponse(packet, length, query)
            writeToTun(tunOut, servfail)
            return
        }

        val packetCopy = packet.copyOf(length)
        val allowReason = result.reason.name
        forwardExecutor.execute {
            try {
                forwardQueryWithRetry(packetCopy, length, query, allowReason, tunOut)
            } finally {
                inflightSemaphore.release()
            }
        }
    }

    private fun forwardQueryWithRetry(
        packet: ByteArray,
        length: Int,
        query: DnsQuery,
        allowReason: String,
        tunOut: FileOutputStream
    ) {
        val resolvers = getSelectedResolvers()
        if (resolvers.isEmpty()) {
            decisionBuffer.record(
                rawDomain = query.domain,
                qType = query.qType,
                decision = DnsBufferDecision.UPSTREAM_ERROR,
                reason = "NO_UPSTREAM_RESOLVER",
                isPrivateRule = false,
                upstreamLatencyMs = 0L
            )
            recordUpstreamFailureAndRespondServfail(packet, query, tunOut)
            return
        }

        val dnsPayload = packet.copyOfRange(query.dnsPayloadOffset, query.dnsPayloadOffset + query.dnsPayloadLength)

        // Attempt 1 + bounded 1 retry (up to 2 resolvers or same resolver twice)
        val targets = if (resolvers.size >= 2) resolvers.take(2) else listOf(resolvers[0], resolvers[0])
        val startNs = System.nanoTime()

        for (upstreamAddress in targets) {
            val responseBytes = queryUpstreamUdpThenTcpFallback(upstreamAddress, dnsPayload)
            if (responseBytes != null) {
                val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
                healthMonitor.recordSuccess()
                decisionBuffer.record(
                    rawDomain = query.domain,
                    qType = query.qType,
                    decision = DnsBufferDecision.ALLOWED,
                    reason = allowReason,
                    isPrivateRule = false,
                    upstreamLatencyMs = latencyMs
                )
                val ipPacket = DnsPacketParser.buildResponseIpPacket(
                    originalQueryPacket = packet,
                    query = query,
                    dnsResponsePayload = responseBytes,
                    dnsResponseLength = responseBytes.size
                )
                writeToTun(tunOut, ipPacket)
                return
            }
        }

        val latencyMs = (System.nanoTime() - startNs) / 1_000_000L
        decisionBuffer.record(
            rawDomain = query.domain,
            qType = query.qType,
            decision = DnsBufferDecision.TIMEOUT,
            reason = "UPSTREAM_TIMEOUT",
            isPrivateRule = false,
            upstreamLatencyMs = latencyMs
        )
        // Never convert upstream errors into NXDOMAIN: return SERVFAIL after 1 retry
        recordUpstreamFailureAndRespondServfail(packet, query, tunOut)
    }

    private fun recordUpstreamFailureAndRespondServfail(
        packet: ByteArray,
        query: DnsQuery,
        tunOut: FileOutputStream
    ) {
        val servfail = DnsPacketParser.buildServfailResponse(packet, packet.size, query)
        writeToTun(tunOut, servfail)

        if (healthMonitor.recordFailure()) {
            Log.w(TAG, "Upstream error threshold exceeded; failing OPEN")
            triggerFailOpen("Network DNS unreachable")
        }
    }

    private fun queryUpstreamUdpThenTcpFallback(
        upstreamAddress: InetAddress,
        dnsPayload: ByteArray
    ): ByteArray? {
        var udpSocket: DatagramSocket? = null
        try {
            udpSocket = DatagramSocket()
            bindAndProtectSocket(udpSocket)
            udpSocket.soTimeout = UPSTREAM_TIMEOUT_MS

            val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddress, 53)
            udpSocket.send(outPacket)

            val inBuffer = ByteArray(4096)
            val inPacket = DatagramPacket(inBuffer, inBuffer.size)
            udpSocket.receive(inPacket)

            val udpResp = inPacket.data.copyOf(inPacket.length)
            if (DnsPacketParser.isTruncatedResponse(udpResp, udpResp.size)) {
                return queryUpstreamTcp(upstreamAddress, dnsPayload) ?: udpResp
            }
            return udpResp
        } catch (_: Exception) {
            return null
        } finally {
            try { udpSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun queryUpstreamTcp(
        upstreamAddress: InetAddress,
        dnsPayload: ByteArray
    ): ByteArray? {
        var tcpSocket: Socket? = null
        try {
            tcpSocket = Socket()
            protect(tcpSocket)
            physicalNetwork?.let { net ->
                try { net.bindSocket(tcpSocket) } catch (_: Exception) {}
            }
            tcpSocket.soTimeout = UPSTREAM_TIMEOUT_MS
            tcpSocket.connect(InetSocketAddress(upstreamAddress, 53), UPSTREAM_TIMEOUT_MS)

            val out = DataOutputStream(tcpSocket.getOutputStream())
            out.writeShort(dnsPayload.size)
            out.write(dnsPayload)
            out.flush()

            val inp = DataInputStream(tcpSocket.getInputStream())
            val respLen = inp.readUnsignedShort()
            if (respLen <= 0 || respLen > 65535) return null
            val resp = ByteArray(respLen)
            inp.readFully(resp)
            return resp
        } catch (_: Exception) {
            return null
        } finally {
            try { tcpSocket?.close() } catch (_: Exception) {}
        }
    }

    private fun bindAndProtectSocket(socket: DatagramSocket) {
        protect(socket)
        physicalNetwork?.let { net ->
            try {
                net.bindSocket(socket)
            } catch (_: Exception) {
                // protect(socket) already excludes from VPN
            }
        }
    }

    /**
     * Returns upstream resolvers strictly from the user's selected provider.
     * System Default uses ONLY the active physical network's DNS servers (no silent third-party fallback).
     */
    private fun getSelectedResolvers(): List<InetAddress> {
        if (currentUpstreamProvider != UpstreamResolverProvider.SYSTEM_DEFAULT) {
            return currentUpstreamProvider.addresses.mapNotNull { ip ->
                try { InetAddress.getByName(ip) } catch (_: Exception) { null }
            }
        }

        val linkProps = physicalLinkProps ?: run {
            refreshPhysicalNetwork()
            physicalLinkProps
        }

        val resolvers = mutableListOf<InetAddress>()
        linkProps?.dnsServers?.forEach { dns ->
            val host = dns.hostAddress ?: ""
            if (!dns.isLoopbackAddress &&
                !dns.isAnyLocalAddress &&
                host != "10.0.0.1" &&
                host != "fd00::1" &&
                host != "192.0.0.1"
            ) {
                resolvers.add(dns)
            }
        }
        return resolvers
    }

    private fun writeToTun(tunOut: FileOutputStream, data: ByteArray) {
        synchronized(tunOut) {
            try {
                tunOut.write(data)
            } catch (e: Exception) {
                if (isRunning) Log.w(TAG, "TUN write error: ${e.message}")
            }
        }
    }

    private fun registerPhysicalNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                physicalNetwork = network
                physicalLinkProps = cm.getLinkProperties(network)
                updatePrivateDnsState(physicalLinkProps)
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                if (network == physicalNetwork || physicalNetwork == null) {
                    physicalNetwork = network
                    physicalLinkProps = linkProperties
                    updatePrivateDnsState(linkProperties)
                }
            }

            override fun onLost(network: Network) {
                if (network == physicalNetwork) {
                    physicalNetwork = null
                    physicalLinkProps = null
                    refreshPhysicalNetwork()
                }
            }
        }
        networkCallback = cb
        try {
            cm.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register network callback: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun refreshPhysicalNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        for (net in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(net) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                physicalNetwork = net
                physicalLinkProps = cm.getLinkProperties(net)
                updatePrivateDnsState(physicalLinkProps)
                return
            }
        }
        updatePrivateDnsState(null)
    }

    private fun updatePrivateDnsState(linkProperties: LinkProperties?) {
        val strict = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && linkProperties != null) {
            linkProperties.isPrivateDnsActive && linkProperties.privateDnsServerName != null
        } else {
            val mode = try {
                Settings.Global.getString(contentResolver, "private_dns_mode")
            } catch (_: Exception) { null }
            mode.equals("hostname", ignoreCase = true)
        }
        _isPrivateDnsStrict.value = strict
    }

    private fun registerAirplaneReceiver() {
        if (!isAirplaneReceiverRegistered) {
            registerReceiver(airplaneReceiver, IntentFilter(Intent.ACTION_AIRPLANE_MODE_CHANGED))
            isAirplaneReceiverRegistered = true
        }
    }

    private fun triggerFailOpen(reason: String) {
        _isFailOpenPaused.value = true
        _lastFailOpenReason.value = reason
        serviceScope.launch {
            policyStore.setDnsProtectionEnabled(false)
        }
        showFailOpenAlertNotification()
        stopVpn()
    }

    private fun startForegroundWithNotification() {
        val notification = buildActiveNotification(_todayBlockedCount.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateActiveNotification(blockedToday: Int) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildActiveNotification(blockedToday))
    }

    private fun buildActiveNotification(blockedToday: Int): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            202,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_PAUSE_5M
        }
        val pausePendingIntent = PendingIntent.getService(
            this,
            203,
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (blockedToday > 0) "$blockedToday blocked today" else "0 blocked today"

        return NotificationCompat.Builder(this, FocusForgeApp.VPN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focus rules active")
            .setContentText(text)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Pause 5m", pausePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun showFailOpenAlertNotification() {
        val restartIntent = Intent(this, DnsVpnService::class.java).apply {
            action = ACTION_RESUME_NOW
        }
        val restartPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(
                this,
                204,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        } else {
            PendingIntent.getService(
                this,
                204,
                restartIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(this, FocusForgeApp.VPN_ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Protection paused: network issue")
            .setContentText("Internet access remains open. Tap to restart protection.")
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setOngoing(true)
            .addAction(0, "Restart", restartPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(FAIL_OPEN_NOTIFICATION_ID, notification)
    }

    private fun clearFailOpenAlertNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.cancel(FAIL_OPEN_NOTIFICATION_ID)
    }

    private fun stopVpn() {
        pauseTunnelOnly()
        decisionBuffer.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN revoked by user or system; failing open")
        serviceScope.launch {
            policyStore.setDnsProtectionEnabled(false)
        }
        triggerFailOpen("VPN permission revoked")
        super.onRevoke()
    }

    override fun onDestroy() {
        pauseTunnelOnly()
        networkCallback?.let { cb ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            try { cm?.unregisterNetworkCallback(cb) } catch (_: Exception) {}
        }
        if (isAirplaneReceiverRegistered) {
            try { unregisterReceiver(airplaneReceiver) } catch (_: Exception) {}
            isAirplaneReceiverRegistered = false
        }
        forwardExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DnsVpnService"
        const val NOTIFICATION_ID = 2002
        const val FAIL_OPEN_NOTIFICATION_ID = 2003
        private const val UPSTREAM_TIMEOUT_MS = 2000
        private const val MAX_CONCURRENT_QUERIES = 32

        const val ACTION_START_VPN = "com.focusforge.app.action.START_VPN"
        const val ACTION_STOP_VPN = "com.focusforge.app.action.STOP_VPN"
        const val ACTION_PAUSE_5M = "com.focusforge.app.action.PAUSE_5M"
        const val ACTION_RESUME_NOW = "com.focusforge.app.action.RESUME_NOW"

        private val _isVpnRunning = MutableStateFlow(false)
        val isVpnRunning: StateFlow<Boolean> = _isVpnRunning.asStateFlow()

        private val _todayBlockedCount = MutableStateFlow(0)
        val todayBlockedCount: StateFlow<Int> = _todayBlockedCount.asStateFlow()

        private val _isPrivateDnsStrict = MutableStateFlow(false)
        val isPrivateDnsStrict: StateFlow<Boolean> = _isPrivateDnsStrict.asStateFlow()

        private val _isFailOpenPaused = MutableStateFlow(false)
        val isFailOpenPaused: StateFlow<Boolean> = _isFailOpenPaused.asStateFlow()

        private val _lastFailOpenReason = MutableStateFlow<String?>(null)
        val lastFailOpenReason: StateFlow<String?> = _lastFailOpenReason.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_START_VPN
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, DnsVpnService::class.java).apply {
                action = ACTION_STOP_VPN
            }
            context.startService(intent)
        }
    }
}
