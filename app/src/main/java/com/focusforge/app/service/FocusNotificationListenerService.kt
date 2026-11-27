package com.focusforge.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.repository.BlockedAppRepository
import com.focusforge.app.data.repository.PolicyStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Silences notifications from blocked applications during active focus hours
 * (when a blocking profile is active or a focus timer is running).
 */
@AndroidEntryPoint
class FocusNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var policyStore: PolicyStore

    @Inject
    lateinit var blockedAppRepository: BlockedAppRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var blockedPackages: Set<String> = emptySet()

    @Volatile
    private var isFocusActive: Boolean = false

    override fun onListenerConnected() {
        super.onListenerConnected()
        serviceScope.launch {
            blockedAppRepository.getBlockedPackageNames().collect { pkgs ->
                blockedPackages = pkgs.toSet()
                if (isFocusActive) {
                    sweepActiveNotifications()
                }
            }
        }
        serviceScope.launch {
            combine(
                policyStore.policySnapshot,
                TimerService.timerState,
                AppBlockerService.isServiceRunning,
                AppBlockerService.isBypassActive
            ) { snapshot, timerState, blockerRunning, bypassActive ->
                val profileActive = snapshot.activeProfile != BlockingProfile.OFF && !snapshot.isPaused(System.currentTimeMillis())
                val timerRunning = timerState.status == TimerStatus.RUNNING
                val shieldActive = blockerRunning && !bypassActive
                profileActive || timerRunning || shieldActive
            }.collect { active ->
                isFocusActive = active
                if (active) {
                    sweepActiveNotifications()
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val posted = sbn ?: return
        if (!shouldSuppress(posted.packageName)) return
        try {
            cancelNotification(posted.key)
        } catch (_: Exception) {
            // Safe no-op if notification already dismissed
        }
    }

    private fun sweepActiveNotifications() {
        try {
            activeNotifications?.forEach { sbn ->
                if (shouldSuppress(sbn.packageName)) {
                    cancelNotification(sbn.key)
                }
            }
        } catch (_: Exception) {
            // Listener may not be bound yet
        }
    }

    private fun shouldSuppress(pkg: String?): Boolean {
        if (pkg.isNullOrBlank()) return false
        if (!isFocusActive) return false
        if (pkg == packageName) return false
        return pkg in blockedPackages
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
