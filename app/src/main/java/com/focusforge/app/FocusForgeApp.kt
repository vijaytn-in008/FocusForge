package com.focusforge.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class FocusForgeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    /**
     * Creates notification channels required for foreground services and session alerts.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val timerChannel = NotificationChannel(
                TIMER_CHANNEL_ID,
                getString(R.string.timer_notification_channel_name),
                // IMPORTANCE_LOW prevents the phone from buzzing every second during countdown updates
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.timer_notification_channel_desc)
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(
                ALERT_CHANNEL_ID,
                "Session Completed Alert",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts triggered when a focus block finishes"
                enableVibration(true)
            }

            val blockerChannel = NotificationChannel(
                BLOCKER_CHANNEL_ID,
                "App Blocker Shield",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for distraction app monitoring"
                setShowBadge(false)
            }

            val vpnChannel = NotificationChannel(
                VPN_CHANNEL_ID,
                "Focus Rules Guard",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing notification for local DNS filtering"
                setShowBadge(false)
            }

            val vpnAlertChannel = NotificationChannel(
                VPN_ALERT_CHANNEL_ID,
                "Protection Status Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts when DNS protection pauses due to a network issue"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannels(
                listOf(timerChannel, alertChannel, blockerChannel, vpnChannel, vpnAlertChannel)
            )
        }
    }

    companion object {
        const val TIMER_CHANNEL_ID = "focusforge_timer_channel"
        const val ALERT_CHANNEL_ID = "focusforge_alert_channel"
        const val BLOCKER_CHANNEL_ID = "focusforge_blocker_channel"
        const val VPN_CHANNEL_ID = "focusforge_vpn_status_channel"
        const val VPN_ALERT_CHANNEL_ID = "focusforge_vpn_alert_channel"
    }
}
