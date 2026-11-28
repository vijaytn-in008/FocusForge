package com.focusforge.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Standard flavor notification-silencing helper backed by Do Not Disturb policy access
 * (ACCESS_NOTIFICATION_POLICY). Degrades gracefully when policy access is not granted.
 */
object NotificationSilenceManager {
    private const val PREFS_NAME = "focusforge_dnd_state"
    private const val KEY_DID_ENABLE_DND = "did_enable_dnd"
    private const val KEY_PREVIOUS_FILTER = "previous_filter"

    const val permissionTitle: String = "Do Not Disturb Access"
    const val permissionDescription: String =
        "Silences distracting notifications during focus blocks via Do Not Disturb."

    fun isPermissionGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false
        return nm.isNotificationPolicyAccessGranted
    }

    fun getPermissionIntent(): Intent {
        return Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    }

    @Synchronized
    fun syncFocusState(context: Context, isFocusActive: Boolean) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        if (!nm.isNotificationPolicyAccessGranted) {
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val didEnableDnd = prefs.getBoolean(KEY_DID_ENABLE_DND, false)

        try {
            if (isFocusActive && !didEnableDnd) {
                val currentFilter = nm.currentInterruptionFilter
                prefs.edit()
                    .putInt(KEY_PREVIOUS_FILTER, currentFilter)
                    .putBoolean(KEY_DID_ENABLE_DND, true)
                    .apply()
                if (currentFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            } else if (!isFocusActive && didEnableDnd) {
                val previousFilter = prefs.getInt(
                    KEY_PREVIOUS_FILTER,
                    NotificationManager.INTERRUPTION_FILTER_ALL
                )
                prefs.edit()
                    .putBoolean(KEY_DID_ENABLE_DND, false)
                    .apply()
                nm.setInterruptionFilter(previousFilter)
            }
        } catch (_: SecurityException) {
            // Degrade gracefully if DND policy access was revoked
        } catch (_: Exception) {
            // Degrade gracefully on OEM-specific interruption filter quirks
        }
    }
}
