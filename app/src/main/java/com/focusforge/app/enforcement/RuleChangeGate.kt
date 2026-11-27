package com.focusforge.app.enforcement

import android.util.Log
import com.focusforge.app.data.repository.PolicyStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub for Phase 5 tamper protection.
 * Strengthening actions apply immediately.
 * Temporary 5-minute pause applies immediately and is counted locally.
 * Permanent weakening actions will be delayed by Phase 5.
 */
@Singleton
class RuleChangeGate @Inject constructor(
    private val policyStore: PolicyStore
) {
    suspend fun pauseProtectionFor5Minutes(nowMillis: Long = System.currentTimeMillis()) {
        val until = nowMillis + PAUSE_DURATION_MS
        policyStore.setPauseUntil(until)
        policyStore.recordPauseEvent()
        Log.i(TAG, "5-minute protection pause activated until $until")
    }

    suspend fun resumeProtectionNow() {
        policyStore.setPauseUntil(null)
        Log.i(TAG, "Protection resumed immediately")
    }

    suspend fun requestPermanentWeakening(actionName: String, execute: suspend () -> Unit) {
        // Phase 3 stub: executes immediately; Phase 5 adds tamper delay + pending countdown
        Log.d(TAG, "RuleChangeGate stub: executing $actionName (Phase 5 will add delay)")
        execute()
    }

    companion object {
        private const val TAG = "RuleChangeGate"
        const val PAUSE_DURATION_MS = 5 * 60 * 1000L
    }
}
