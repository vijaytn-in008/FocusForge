package com.focusforge.app.enforcement

import android.util.Log
import com.focusforge.app.data.repository.PolicyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pending weakening change state (never contains any domain or app name!).
 */
data class PendingPrivateChange(
    val remainingSeconds: Int
)

/**
 * Stub for Phase 5 tamper protection.
 * Strengthening actions apply immediately.
 * Temporary 5-minute pause applies immediately and is counted locally.
 * Removing/disabling a private rule goes through a pending countdown with Cancel,
 * never exposing the rule's name.
 */
@Singleton
class RuleChangeGate @Inject constructor(
    private val policyStore: PolicyStore
) {
    private val gateScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var pendingJob: Job? = null

    private val _pendingPrivateChange = MutableStateFlow<PendingPrivateChange?>(null)
    val pendingPrivateChange: StateFlow<PendingPrivateChange?> = _pendingPrivateChange.asStateFlow()

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
        // Phase 3 stub for general settings; Phase 5 will add configurable delay
        Log.d(TAG, "RuleChangeGate stub: executing $actionName")
        execute()
    }

    /**
     * Schedules removal of a private rule after [delaySeconds].
     * UI displays only "1 private rule pending change" + countdown + Cancel.
     */
    fun schedulePrivateRuleRemoval(delaySeconds: Int = PRIVATE_WEAKEN_DELAY_SECONDS, execute: suspend () -> Unit) {
        pendingJob?.cancel()
        pendingJob = gateScope.launch {
            for (sec in delaySeconds downTo 1) {
                _pendingPrivateChange.value = PendingPrivateChange(sec)
                delay(1000L)
            }
            _pendingPrivateChange.value = null
            execute()
        }
    }

    fun cancelPendingPrivateChange() {
        pendingJob?.cancel()
        pendingJob = null
        _pendingPrivateChange.value = null
    }

    companion object {
        private const val TAG = "RuleChangeGate"
        const val PAUSE_DURATION_MS = 5 * 60 * 1000L
        const val PRIVATE_WEAKEN_DELAY_SECONDS = 15
    }
}
