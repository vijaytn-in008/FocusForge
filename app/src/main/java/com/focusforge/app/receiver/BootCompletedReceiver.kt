package com.focusforge.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.focusforge.app.data.repository.UserPreferencesRepository
import com.focusforge.app.service.AppBlockerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Listens for system boot completion and automatically resumes AppBlockerService
 * if the user had it enabled.
 */
@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val prefs = context.getSharedPreferences("focusforge_prefs", Context.MODE_PRIVATE)
            val isBlockerEnabled = prefs.getBoolean("app_blocker_enabled", false)
            val isDnsEnabled = prefs.getBoolean("dns_protection_enabled", false)

            if (isBlockerEnabled) {
                AppBlockerService.start(context)
            }
            if (isDnsEnabled) {
                com.focusforge.app.enforcement.DnsVpnService.start(context)
            }
        }
    }
}
