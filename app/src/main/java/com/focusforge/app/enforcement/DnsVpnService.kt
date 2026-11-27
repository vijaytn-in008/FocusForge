package com.focusforge.app.enforcement

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.focusforge.app.FocusForgeApp
import com.focusforge.app.MainActivity
import com.focusforge.app.R
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.repository.PolicyRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import javax.inject.Inject

/**
 * Local-only DNS VPN Service for adult and distraction website blocking.
 * Routes strictly the virtual DNS server addresses (10.0.0.1/32 and fd00::1/128).
 * All general internet traffic bypasses this service and never enters the app.
 */
@AndroidEntryPoint
class DnsVpnService : VpnService() {

    @Inject
    lateinit var policyRepository: PolicyRepository

    @Inject
    lateinit var blocklistEngine: BlocklistEngine

    private var vpnInterface: ParcelFileDescriptor? = null
    private var isRunning = false
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private var currentPolicy = PolicySnapshot()

    // Fallback public DNS resolvers (Cloudflare IPv6, Jio IPv6, Google/Cloudflare IPv4)
    private val fallbackDnsIps = listOf(
        "2606:4700:4700::1111",
        "2001:4860:4860::8888",
        "8.8.8.8",
        "1.1.1.1"
    )
    // Bounded executor for asynchronous DNS forwarding
    private val forwardExecutor = Executors.newFixedThreadPool(4)

    private var vpnThread: Thread? = null

    override fun onCreate() {
        super.onCreate()
        _isVpnRunning.value = true
        startForegroundWithNotification()

        serviceScope.launch {
            policyRepository.policySnapshot.collect { snapshot ->
                currentPolicy = snapshot
                Log.d(TAG, "DnsVpnService received updated policy: enabledCategories=${snapshot.enabledDomainCategories}")
                if (!snapshot.isDnsBlockingEffective && isRunning) {
                    Log.i(TAG, "No DNS categories active, stopping VPN")
                    stopVpn()
                }
            }
        }

        serviceScope.launch {
            policyRepository.getTodayBlockedCount().collect { count ->
                _todayBlockedCount.value = count
                updateNotification()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_VPN -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpnTunnel()
                return START_STICKY
            }
        }
    }

    private fun startVpnTunnel() {
        if (isRunning) return

        try {
            val builder = Builder()
                .setSession("FocusForge DNS Guard")
                // IPv4 DNS tunnel
                .addAddress("10.0.0.2", 32)
                .addDnsServer("10.0.0.1")
                .addRoute("10.0.0.1", 32)
                // IPv6 DNS tunnel
                .addAddress("fd00::2", 128)
                .addDnsServer("fd00::1")
                .addRoute("fd00::1", 128)
                .setMtu(1500)
                .setBlocking(true)

            // 3d Requirement: Disallow our own app so internal HTTP/models/sync bypass VPN
            try {
                builder.addDisallowedApplication(packageName)
            } catch (e: Exception) {
                Log.w(TAG, "Could not exclude own package: ${e.message}")
            }

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "Failed to establish VPN interface (null)")
                stopSelf()
                return
            }

            isRunning = true
            _isVpnRunning.value = true
            updateNotification()

            vpnThread = Thread({ runVpnLoop() }, "FocusForge-DnsTun").apply { start() }
            Log.i(TAG, "FocusForge DNS VPN tunnel established successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Exception establishing VPN tunnel: ${e.message}", e)
            stopVpn()
        }
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

                val query = DnsPacketParser.parseDnsQuery(packetBuffer, bytesRead)
                if (query != null) {
                    handleDnsQuery(packetBuffer, bytesRead, query, outputStream)
                }
            }
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "TUN read error: ${e.message}")
            }
        } finally {
            try {
                inputStream.close()
                outputStream.close()
            } catch (_: Exception) {}
        }
    }

    private fun handleDnsQuery(
        packet: ByteArray,
        length: Int,
        query: DnsQuery,
        tunOut: FileOutputStream
    ) {
        val checkResult = blocklistEngine.checkDomain(
            rawDomain = query.domain,
            enabledCategories = currentPolicy.enabledDomainCategories,
            customBlockedDomains = currentPolicy.customBlockedDomains,
            customAllowedDomains = currentPolicy.customAllowedDomains
        )

        if (checkResult.isBlocked) {
            Log.i(TAG, "BLOCKED query for: ${query.domain} [Rule: ${checkResult.matchedRule}]")

            // Log block asynchronously
            serviceScope.launch {
                policyRepository.logDnsBlock(
                    domain = query.domain,
                    category = checkResult.matchedCategory?.name ?: "CUSTOM",
                    wasBlocked = true
                )
            }

            // Generate NXDOMAIN response and write back to TUN interface
            val nxResponse = DnsPacketParser.buildNxdomainResponse(packet, length, query)
            synchronized(tunOut) {
                try {
                    tunOut.write(nxResponse)
                } catch (e: Exception) {
                    Log.w(TAG, "Error writing NXDOMAIN to TUN: ${e.message}")
                }
            }
        } else {
            // Forward query to upstream resolver
            forwardExecutor.execute {
                forwardQueryToUpstream(packet, length, query, tunOut)
            }
        }
    }

    private fun getUpstreamResolvers(): List<InetAddress> {
        val resolvers = mutableListOf<InetAddress>()
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = cm?.activeNetwork
            if (activeNetwork != null) {
                val linkProps = cm.getLinkProperties(activeNetwork)
                linkProps?.dnsServers?.forEach { dns ->
                    val host = dns.hostAddress ?: ""
                    // Filter out our own virtual VPN DNS IP endpoints
                    if (!dns.isLoopbackAddress && !dns.isAnyLocalAddress && host != "10.0.0.1" && host != "fd00::1") {
                        // Avoid unroutable carrier CGNAT dummy DNS IP
                        if (host != "192.0.0.1") {
                            resolvers.add(dns)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve dynamic upstream DNS from LinkProperties: ${e.message}")
        }

        // Add public DNS fallbacks
        for (ip in fallbackDnsIps) {
            try {
                val addr = InetAddress.getByName(ip)
                if (!resolvers.contains(addr)) {
                    resolvers.add(addr)
                }
            } catch (_: Exception) {}
        }

        return resolvers
    }

    private fun forwardQueryToUpstream(
        packet: ByteArray,
        length: Int,
        query: DnsQuery,
        tunOut: FileOutputStream
    ) {
        val resolvers = getUpstreamResolvers()
        if (resolvers.isEmpty()) {
            Log.w(TAG, "No upstream resolvers available for ${query.domain}")
            return
        }

        val dnsPayload = packet.copyOfRange(query.dnsPayloadOffset, query.dnsPayloadOffset + query.dnsPayloadLength)
        val inBuffer = ByteArray(4096)

        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val underlyingNetwork = cm?.activeNetwork

        // Try resolvers in order until one responds
        for (upstreamAddress in resolvers.take(4)) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                // Bind socket explicitly to the physical network if available, otherwise protect()
                if (underlyingNetwork != null) {
                    try {
                        underlyingNetwork.bindSocket(socket)
                    } catch (e: Exception) {
                        protect(socket)
                    }
                } else {
                    protect(socket)
                }

                socket.soTimeout = 1500 // 1.5s fast timeout per resolver

                val outPacket = DatagramPacket(dnsPayload, dnsPayload.size, upstreamAddress, 53)
                socket.send(outPacket)

                val inPacket = DatagramPacket(inBuffer, inBuffer.size)
                socket.receive(inPacket)

                val responseIpPacket = buildResponseIpPacket(packet, query, inPacket.data, inPacket.length)
                synchronized(tunOut) {
                    try {
                        tunOut.write(responseIpPacket)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error writing forwarded response to TUN: ${e.message}")
                    }
                }
                return // Success!
            } catch (e: Exception) {
                Log.d(TAG, "Resolver ${upstreamAddress.hostAddress} failed or timed out for ${query.domain}: ${e.message}")
            } finally {
                socket?.close()
            }
        }
        Log.w(TAG, "All upstream resolvers timed out or failed for ${query.domain}")
    }

    private fun buildResponseIpPacket(
        originalQueryPacket: ByteArray,
        query: DnsQuery,
        dnsResponsePayload: ByteArray,
        dnsResponseLength: Int
    ): ByteArray {
        val isIpv6 = query.isIpv6
        val ipHeaderLen = if (isIpv6) 40 else 20
        val totalLen = ipHeaderLen + 8 + dnsResponseLength
        val resp = ByteArray(totalLen)

        // 1. IP Header
        if (!isIpv6) {
            resp[0] = 0x45.toByte() // Version 4, IHL 5
            resp[1] = 0
            resp[2] = ((totalLen shr 8) and 0xFF).toByte()
            resp[3] = (totalLen and 0xFF).toByte()
            resp[4] = 0; resp[5] = 0 // ID
            resp[6] = 0x40.toByte(); resp[7] = 0 // Don't fragment
            resp[8] = 64 // TTL
            resp[9] = 17 // UDP

            // Source IP = original query's destination IP; Destination IP = original query's source IP
            System.arraycopy(originalQueryPacket, 16, resp, 12, 4)
            System.arraycopy(originalQueryPacket, 12, resp, 16, 4)

            // IP checksum
            computeIpv4Checksum(resp, ipHeaderLen)
        } else {
            resp[0] = 0x60.toByte()
            resp[4] = (((8 + dnsResponseLength) shr 8) and 0xFF).toByte()
            resp[5] = ((8 + dnsResponseLength) and 0xFF).toByte()
            resp[6] = 17 // Next header UDP
            resp[7] = 64 // Hop limit
            // Src = original Dst, Dst = original Src
            System.arraycopy(originalQueryPacket, 24, resp, 8, 16)
            System.arraycopy(originalQueryPacket, 8, resp, 24, 16)
        }

        // 2. UDP Header
        val udpOffset = ipHeaderLen
        val udpLen = 8 + dnsResponseLength
        // Src Port = original query's Dst Port (53)
        resp[udpOffset] = originalQueryPacket[udpOffset + 2]
        resp[udpOffset + 1] = originalQueryPacket[udpOffset + 3]
        // Dst Port = original query's Src Port
        resp[udpOffset + 2] = originalQueryPacket[udpOffset]
        resp[udpOffset + 3] = originalQueryPacket[udpOffset + 1]
        resp[udpOffset + 4] = ((udpLen shr 8) and 0xFF).toByte()
        resp[udpOffset + 5] = (udpLen and 0xFF).toByte()
        resp[udpOffset + 6] = 0; resp[udpOffset + 7] = 0 // Zero checksum

        // 3. DNS Response Payload
        System.arraycopy(dnsResponsePayload, 0, resp, udpOffset + 8, dnsResponseLength)

        return resp
    }

    private fun computeIpv4Checksum(packet: ByteArray, headerLen: Int) {
        packet[10] = 0; packet[11] = 0
        var sum = 0
        var i = 0
        while (i < headerLen) {
            val word = ((packet[i].toInt() and 0xFF) shl 8) or (packet[i + 1].toInt() and 0xFF)
            sum += word
            i += 2
        }
        while ((sum shr 16) > 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val cs = (sum.inv()) and 0xFFFF
        packet[10] = ((cs shr 8) and 0xFF).toByte()
        packet[11] = (cs and 0xFF).toByte()
    }

    private fun updateNotification() {
        val count = _todayBlockedCount.value
        val text = if (count > 0) "$count distraction & adult sites blocked today" else "Guard is actively filtering domain requests"
        val notification = buildNotification(text)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun startForegroundWithNotification() {
        try {
            val initialNotification = buildNotification("Starting DNS guard...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground for DnsVpnService: ${e.message}")
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            202,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FocusForgeApp.VPN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DNS Protection Active")
            .setContentText(contentText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopVpn() {
        isRunning = false
        _isVpnRunning.value = false
        vpnThread?.interrupt()
        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "FocusForge DNS VPN service stopped")
    }

    override fun onRevoke() {
        Log.w(TAG, "VPN permission revoked by user or system")
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        forwardExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "DnsVpnService"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START_VPN = "com.focusforge.app.action.START_VPN"
        const val ACTION_STOP_VPN = "com.focusforge.app.action.STOP_VPN"

        private val _isVpnRunning = MutableStateFlow(false)
        val isVpnRunning: StateFlow<Boolean> = _isVpnRunning.asStateFlow()

        private val _todayBlockedCount = MutableStateFlow(0)
        val todayBlockedCount: StateFlow<Int> = _todayBlockedCount.asStateFlow()

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
