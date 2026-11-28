package com.focusforge.app.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.focusforge.app.data.model.PolicySnapshot

/**
 * Pure gating predicate for the Phase 2 foreground-app watcher.
 * Separated from Android Context for direct unit testing.
 */
object WatcherGateLogic {
    /**
     * Polls ONLY while (screen on) AND (a profile with app blocking is active)
     * AND (blocked app set non-empty) AND (protection not paused).
     */
    fun shouldPoll(
        isScreenInteractive: Boolean,
        snapshot: PolicySnapshot,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return isScreenInteractive && snapshot.isAppBlockingEffective(nowMillis)
    }
}

/**
 * Manages screen ON/OFF gating for the Phase 2 AppBlockerService.
 * Dynamically registers broadcast receivers for ACTION_SCREEN_ON and ACTION_SCREEN_OFF.
 */
class PollingGate(
    private val context: Context,
    private val onScreenStateChanged: (isInteractive: Boolean) -> Unit
) {
    private var isReceiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    Log.d(TAG, "Screen ON -> re-evaluating watcher gate")
                    onScreenStateChanged(true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen OFF -> stopping watcher polling")
                    onScreenStateChanged(false)
                }
            }
        }
    }

    fun isScreenInteractive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: true
    }

    fun startListening() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            ContextCompat.registerReceiver(
                context,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
    }

    fun stopListening() {
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(screenReceiver)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering screen receiver: ${e.message}")
            }
            isReceiverRegistered = false
        }
    }

    companion object {
        private const val TAG = "PollingGate"
    }
}
