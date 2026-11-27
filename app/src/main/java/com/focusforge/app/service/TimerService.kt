package com.focusforge.app.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.focusforge.app.FocusForgeApp
import com.focusforge.app.MainActivity
import com.focusforge.app.R
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.util.TimeUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TimerStatus {
    IDLE,
    RUNNING,
    PAUSED,
    COMPLETED
}

data class TimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val subject: String = "",
    val topic: String = "",
    val totalSeconds: Int = 25 * 60,
    val remainingSeconds: Int = 25 * 60,
    val sessionType: SessionType = SessionType.DEEP,
    val startTimeMillis: Long = 0L
)

/**
 * Foreground service that manages the focus countdown timer.
 * Runs independently of UI lifecycle so sessions never drop on app minimization or system trim.
 */
class TimerService : Service() {

    private var countDownTimer: CountDownTimer? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeLock()
        // Immediately attach foreground notification to satisfy Android 14/15 strict foreground launch window
        startForegroundWithNotification(_timerState.value)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "General Focus"
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
                val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, 25 * 60)
                val typeName = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: SessionType.DEEP.name
                val sessionType = runCatching { SessionType.valueOf(typeName) }.getOrDefault(SessionType.DEEP)

                val newState = TimerState(
                    status = TimerStatus.RUNNING,
                    subject = subject,
                    topic = topic,
                    totalSeconds = durationSeconds,
                    remainingSeconds = durationSeconds,
                    sessionType = sessionType,
                    startTimeMillis = System.currentTimeMillis()
                )
                _timerState.value = newState
                startForegroundWithNotification(newState)
                startCountdown(durationSeconds)
            }
            ACTION_PAUSE -> {
                pauseCountdown()
            }
            ACTION_RESUME -> {
                resumeCountdown()
            }
            ACTION_STOP -> {
                stopTimerService()
            }
        }
        return START_NOT_STICKY
    }

    private fun startCountdown(seconds: Int) {
        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer((seconds * 1000L), 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val remainingSec = (millisUntilFinished / 1000L).toInt()
                _timerState.value = _timerState.value.copy(
                    remainingSeconds = remainingSec,
                    status = TimerStatus.RUNNING
                )
                updateNotification(_timerState.value)
            }

            override fun onFinish() {
                _timerState.value = _timerState.value.copy(
                    remainingSeconds = 0,
                    status = TimerStatus.COMPLETED
                )
                onTimerCompleted()
            }
        }.start()
    }

    private fun pauseCountdown() {
        countDownTimer?.cancel()
        countDownTimer = null
        val pausedState = _timerState.value.copy(status = TimerStatus.PAUSED)
        _timerState.value = pausedState
        updateNotification(pausedState)
    }

    private fun resumeCountdown() {
        val current = _timerState.value
        if (current.remainingSeconds > 0) {
            _timerState.value = current.copy(status = TimerStatus.RUNNING)
            startCountdown(current.remainingSeconds)
        }
    }

    private fun onTimerCompleted() {
        val completedState = _timerState.value
        showCompletionNotification(completedState)
        launchCompletionFullScreen(completedState)
        // Keep FGS alive briefly so Android 14+ BAL exemption applies to the pop-up launch
        Handler(Looper.getMainLooper()).postDelayed({
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }, 600L)
    }

    private fun launchCompletionFullScreen(state: TimerState) {
        val intent = buildCompletionIntent(state)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pending = PendingIntent.getActivity(
                    this,
                    11,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val opts = ActivityOptions.makeBasic()
                    .setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                pending.send(this, 0, null, null, null, null, opts.toBundle())
            } else {
                startActivity(intent)
            }
        } catch (_: Exception) {
            if (Settings.canDrawOverlays(this)) {
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    // Handled by full-screen completion notification
                }
            }
        }
    }

    private fun buildCompletionIntent(state: TimerState): Intent {
        val durationMin = maxOf(1, state.totalSeconds / 60)
        val startTime = if (state.startTimeMillis > 0L) {
            state.startTimeMillis
        } else {
            System.currentTimeMillis() - durationMin * 60_000L
        }
        return Intent(this, com.focusforge.app.TimerCompletionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(com.focusforge.app.TimerCompletionActivity.EXTRA_SUBJECT, state.subject.ifBlank { "General Focus" })
            putExtra(com.focusforge.app.TimerCompletionActivity.EXTRA_TOPIC, state.topic)
            putExtra(com.focusforge.app.TimerCompletionActivity.EXTRA_START_TIME, startTime)
            putExtra(com.focusforge.app.TimerCompletionActivity.EXTRA_DURATION_MIN, durationMin)
            putExtra(com.focusforge.app.TimerCompletionActivity.EXTRA_DURATION_SECONDS, state.totalSeconds.coerceAtLeast(1))
            putExtra(com.focusforge.app.TimerCompletionActivity.EXTRA_SESSION_TYPE, state.sessionType.name)
        }
    }

    private fun stopTimerService() {
        countDownTimer?.cancel()
        countDownTimer = null
        _timerState.value = TimerState(status = TimerStatus.IDLE)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundWithNotification(state: TimerState) {
        try {
            val notification = buildNotification(state)
            // Android 14+ (API 34/35) requires explicit foreground service type
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
            android.util.Log.e("TimerService", "Failed to start foreground service notification: ${e.message}")
        }
    }

    private fun updateNotification(state: TimerState) {
        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(NOTIFICATION_ID, buildNotification(state))
        } catch (_: SecurityException) {
            // Android 13+ runtime notification permission might be revoking
        }
    }

    private fun buildNotification(state: TimerState): Notification {
        val contentTitle = "${TimeUtils.formatSecondsToTime(state.remainingSeconds)} - ${state.subject}"
        val contentText = if (state.topic.isNotBlank()) {
            "(${state.sessionType.name}) ${state.topic}"
        } else {
            "${state.sessionType.name} Session"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_NAV_DESTINATION, "timer")
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, FocusForgeApp.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contentTitle)
            .setContentText(contentText)
            .setOngoing(state.status == TimerStatus.RUNNING)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        // Action buttons
        if (state.status == TimerStatus.RUNNING) {
            val pauseIntent = Intent(this, TimerService::class.java).apply { action = ACTION_PAUSE }
            val pausePending = PendingIntent.getService(
                this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Pause", pausePending)
        } else if (state.status == TimerStatus.PAUSED) {
            val resumeIntent = Intent(this, TimerService::class.java).apply { action = ACTION_RESUME }
            val resumePending = PendingIntent.getService(
                this, 2, resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Resume", resumePending)
        }

        val stopIntent = Intent(this, TimerService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "Stop", stopPending)

        return builder.build()
    }

    private fun showCompletionNotification(state: TimerState) {
        val completionIntent = buildCompletionIntent(state)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            10,
            completionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val completionNotification = NotificationCompat.Builder(this, FocusForgeApp.ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Session Complete")
            .setContentText("Study block finished for '${state.subject}'. Tap to record your journey.")
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .setFullScreenIntent(contentPendingIntent, true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val notificationManager = NotificationManagerCompat.from(this)
        try {
            notificationManager.notify(COMPLETION_NOTIFICATION_ID, completionNotification)
        } catch (_: SecurityException) {
            // Handled safely
        }
    }

    /**
     * Prevents deep CPU sleep while user is in active focus session.
     * Uses 3 hours max timeout to protect against battery drain bugs.
     */
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FocusForge::TimerWakeLock"
            )?.apply {
                setReferenceCounted(false)
                acquire(3 * 60 * 60 * 1000L) // 3 hours timeout safety
            }
        } catch (e: Exception) {
            android.util.Log.w("TimerService", "Could not acquire WakeLock: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("TimerService", "Error releasing WakeLock: ${e.message}")
        } finally {
            wakeLock = null
        }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        countDownTimer = null
        releaseWakeLock()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val COMPLETION_NOTIFICATION_ID = 1002

        const val ACTION_START = "com.focusforge.app.action.START"
        const val ACTION_PAUSE = "com.focusforge.app.action.PAUSE"
        const val ACTION_RESUME = "com.focusforge.app.action.RESUME"
        const val ACTION_STOP = "com.focusforge.app.action.STOP"

        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_SESSION_TYPE = "extra_session_type"

        private val _timerState = MutableStateFlow(TimerState())
        val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

        fun startTimer(
            context: Context,
            subject: String,
            topic: String,
            durationSeconds: Int,
            sessionType: SessionType
        ) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_TOPIC, topic)
                putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
                putExtra(EXTRA_SESSION_TYPE, sessionType.name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun pauseTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_PAUSE
            }
            context.startService(intent)
        }

        fun resumeTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_RESUME
            }
            context.startService(intent)
        }

        fun stopTimer(context: Context) {
            val intent = Intent(context, TimerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun resetState() {
            _timerState.value = TimerState(status = TimerStatus.IDLE)
        }
    }
}
