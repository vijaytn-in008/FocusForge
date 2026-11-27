package com.focusforge.app.enforcement

import android.app.AlarmManager
import android.content.Context
import android.util.Log
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.repository.PolicyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AlarmManager stub for profile scheduling plus manual profile switching for testing.
 * Phase 4 will implement scheduled time blocks and exact alarm handling (API 31+/34+
 * SCHEDULE_EXACT_ALARM / canScheduleExactAlarms() checks).
 */
@Singleton
class ProfileScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val policyStore: PolicyStore
) {
    @Suppress("unused")
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    /**
     * Manual profile switch exposed for testing and Settings/Shield UI control.
     */
    suspend fun switchProfile(profile: BlockingProfile) {
        Log.i(TAG, "Manual profile switch -> $profile")
        policyStore.setActiveProfile(profile)
    }

    /**
     * Stub for Phase 4: schedule a profile transition at [triggerAtMillis].
     */
    fun scheduleProfileTransition(triggerAtMillis: Long, targetProfile: BlockingProfile) {
        Log.d(TAG, "Phase 4 stub: scheduleProfileTransition($targetProfile at $triggerAtMillis)")
    }

    companion object {
        private const val TAG = "ProfileScheduler"
    }
}
