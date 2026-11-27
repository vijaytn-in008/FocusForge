package com.focusforge.app.enforcement

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.repository.PolicyRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scheduler stub for managing profile transitions via AlarmManager.
 * In Phase 3, exposes a manual profile switch used for testing and UI control.
 * In Phase 4, will handle exact time block alarms (SCHEDULE_EXACT_ALARM).
 */
@Singleton
class ProfileScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val policyRepository: PolicyRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Manually switches the active profile.
     */
    suspend fun switchProfile(profile: BlockingProfile) {
        Log.i(TAG, "Profile manual switch to: $profile")
        policyRepository.setActiveProfile(profile)
    }

    /**
     * Stub for Phase 4: Schedule profile change at a specific epoch timestamp.
     */
    fun scheduleProfileTransition(triggerAtMillis: Long, targetProfile: BlockingProfile) {
        Log.d(TAG, "Scheduled profile transition to $targetProfile at $triggerAtMillis (stub for Phase 4)")
    }

    companion object {
        private const val TAG = "ProfileScheduler"
    }
}
