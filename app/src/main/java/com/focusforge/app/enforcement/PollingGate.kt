package com.focusforge.app.enforcement

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log

/**
 * Manages the screen ON/OFF gating for polling services.
 * Dynamically registers broadcast receivers for ACTION_SCREEN_ON and ACTION_SCREEN_OFF.
 * In accordance with Section 1 architecture:
 * "Poll the foreground app only while (screen on) AND (a profile with app blocking is active) AND (blocked set non-empty). Stop at screen-off."
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
                    Log.d(TAG, "Screen turned ON -> resuming polling gate if policy allows")
                    onScreenStateChanged(true)
                }
                Intent.ACTION_SCREEN_OFF -> {
                    Log.d(TAG, "Screen turned OFF -> pausing polling gate to save battery")
                    onScreenStateChanged(false)
                }
            }
        }
    }

    /**
     * Checks if the device screen is currently on and interactive.
     */
    fun isScreenInteractive(): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive ?: true
    }

    /**
     * Starts listening for dynamic screen on/off events.
     */
    fun startListening() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            context.registerReceiver(screenReceiver, filter)
            isReceiverRegistered = true
        }
    }

    /**
     * Stops listening and cleans up receiver.
     */
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
