package com.focusforge.app.service

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Full flavor notification-silencing helper backed by NotificationListenerService.
 */
object NotificationSilenceManager {
    const val permissionTitle: String = "Notification Silencing"
    const val permissionDescription: String =
        "Mutes notifications from blocked apps during active focus hours."

    fun isPermissionGranted(context: Context): Boolean {
        return NotificationManagerCompat.getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun getPermissionIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    fun syncFocusState(context: Context, isFocusActive: Boolean) {
        // Handled per-blocked-app by FocusNotificationListenerService in full flavor
    }
}
