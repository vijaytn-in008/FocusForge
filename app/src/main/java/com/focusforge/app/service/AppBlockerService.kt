package com.focusforge.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Telephony
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.focusforge.app.BlockedAppActivity
import com.focusforge.app.FocusForgeApp
import com.focusforge.app.MainActivity
import com.focusforge.app.R
import com.focusforge.app.data.repository.BlockedAppRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that regularly polls the currently active foreground application.
 * If the application is in the user's blocked list and emergency bypass is inactive,
 * it immediately fires the full-screen BlockedAppActivity to redirect user to work.
 */
@AndroidEntryPoint
class AppBlockerService : Service() {

    @Inject
    lateinit var blockedAppRepository: BlockedAppRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())
    private val handler = Handler(Looper.getMainLooper())
    private var isPolling = false

    // Cache of blocked package names for low-latency lookup in the polling loop
    private val blockedPackages = HashSet<String>()

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isPolling) {
                checkForegroundApp()
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        _isServiceRunning.value = true
        startForegroundWithNotification()

        // Collect active blocked apps into our in-memory hash set
        serviceScope.launch {
            blockedAppRepository.getBlockedPackageNames().collect { list ->
                synchronized(blockedPackages) {
                    blockedPackages.clear()
                    blockedPackages.addAll(list)
                }
            }
        }

        startPolling()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_BLOCKER -> {
                stopBlockerService()
            }
            ACTION_TRIGGER_BYPASS -> {
                val durationSec = intent.getIntExtra(EXTRA_BYPASS_DURATION, 60)
                activateBypass(durationSec)
            }
        }
        return START_STICKY
    }

    private fun startPolling() {
        if (!isPolling) {
            isPolling = true
            handler.post(pollRunnable)
        }
    }

    private fun stopPolling() {
        isPolling = false
        handler.removeCallbacks(pollRunnable)
    }

    private fun checkForegroundApp() {
        // If emergency bypass is currently active, allow all apps temporarily
        if (System.currentTimeMillis() < bypassEndTimeMillis) {
            _isBypassActive.value = true
            return
        } else if (_isBypassActive.value) {
            _isBypassActive.value = false
        }

        val foregroundPackage = getForegroundPackageName() ?: return

        // 1. Never block system essential apps (dialer, phone, sms, settings, our app)
        if (isExemptPackage(foregroundPackage)) {
            return
        }

        // 2. Check if the app is blocked
        val isBlocked = synchronized(blockedPackages) {
            blockedPackages.contains(foregroundPackage)
        }

        if (isBlocked) {
            launchBlockedAppOverlay(foregroundPackage)
        }
    }

    /**
     * Determines current top-most package using UsageStatsManager.
     * Tries queryEvents first; if empty, falls back to queryUsageStats sorted by lastTimeUsed.
     */
    private fun getForegroundPackageName(): String? {
        val usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val endTime = System.currentTimeMillis()
        val startTime = endTime - 10000L // Query the last 10 seconds

        // Method 1: queryEvents (most precise when available)
        try {
            val events = usageStatsManager.queryEvents(startTime, endTime)
            val event = UsageEvents.Event()
            var currentTopPackage: String? = null

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    currentTopPackage = event.packageName
                }
            }

            if (currentTopPackage != null) {
                return currentTopPackage
            }
        } catch (e: Exception) {
            android.util.Log.w("AppBlockerService", "queryEvents error: ${e.message}")
        }

        // Method 2: queryUsageStats fallback
        try {
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )
            if (!stats.isNullOrEmpty()) {
                val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
                if (mostRecent != null && (endTime - mostRecent.lastTimeUsed) < 3000L) {
                    return mostRecent.packageName
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("AppBlockerService", "queryUsageStats error: ${e.message}")
        }

        return null
    }

    private fun launchBlockedAppOverlay(blockedPackage: String) {
        val intent = Intent(this, BlockedAppActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BlockedAppActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
        }
        startActivity(intent)
    }

    private fun isExemptPackage(packageName: String): Boolean {
        // Self
        if (packageName == applicationContext.packageName) return true

        // Known hardcoded system packages
        if (packageName in HARDCODED_EXEMPT_PACKAGES) return true

        // Default dialer app
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val defaultDialer = telecomManager?.defaultDialerPackage
            if (defaultDialer != null && packageName == defaultDialer) return true
        } catch (_: Exception) {}

        // Default SMS app
        try {
            val defaultSms = Telephony.Sms.getDefaultSmsPackage(this)
            if (defaultSms != null && packageName == defaultSms) return true
        } catch (_: Exception) {}

        return false
    }

    private fun activateBypass(durationSeconds: Int) {
        bypassEndTimeMillis = System.currentTimeMillis() + (durationSeconds * 1000L)
        _isBypassActive.value = true
        _bypassRemainingSeconds.value = durationSeconds

        // Timer for countdown updates
        handler.post(object : Runnable {
            override fun run() {
                val remaining = ((bypassEndTimeMillis - System.currentTimeMillis()) / 1000L).toInt()
                if (remaining > 0) {
                    _bypassRemainingSeconds.value = remaining
                    handler.postDelayed(this, 1000L)
                } else {
                    _bypassRemainingSeconds.value = 0
                    _isBypassActive.value = false
                }
            }
        })
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            android.util.Log.e("AppBlockerService", "Failed to start foreground blocker: ${e.message}")
        }
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            201,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, FocusForgeApp.BLOCKER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("App Shield Active")
            .setContentText("FocusForge is guarding against distraction apps")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopBlockerService() {
        stopPolling()
        _isServiceRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopPolling()
        _isServiceRunning.value = false
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 2001
        private const val POLL_INTERVAL_MS = 1000L

        const val ACTION_START_BLOCKER = "com.focusforge.app.action.START_BLOCKER"
        const val ACTION_STOP_BLOCKER = "com.focusforge.app.action.STOP_BLOCKER"
        const val ACTION_TRIGGER_BYPASS = "com.focusforge.app.action.TRIGGER_BYPASS"
        const val EXTRA_BYPASS_DURATION = "extra_bypass_duration"

        private var bypassEndTimeMillis = 0L

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        private val _isBypassActive = MutableStateFlow(false)
        val isBypassActive: StateFlow<Boolean> = _isBypassActive.asStateFlow()

        private val _bypassRemainingSeconds = MutableStateFlow(0)
        val bypassRemainingSeconds: StateFlow<Int> = _bypassRemainingSeconds.asStateFlow()

        val HARDCODED_EXEMPT_PACKAGES = setOf(
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
            "com.android.server.telecom",
            "com.android.contacts",
            "com.google.android.contacts",
            "com.android.mms",
            "com.google.android.apps.messaging",
            "com.android.settings",
            "com.android.systemui"
        )

        fun start(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java).apply {
                action = ACTION_START_BLOCKER
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockerService::class.java).apply {
                action = ACTION_STOP_BLOCKER
            }
            context.startService(intent)
        }

        fun triggerBypass(context: Context, durationSeconds: Int = 60) {
            val intent = Intent(context, AppBlockerService::class.java).apply {
                action = ACTION_TRIGGER_BYPASS
                putExtra(EXTRA_BYPASS_DURATION, durationSeconds)
            }
            context.startService(intent)
        }
    }
}
