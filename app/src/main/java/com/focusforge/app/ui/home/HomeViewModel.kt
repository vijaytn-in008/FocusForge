package com.focusforge.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.repository.BlockedAppRepository
import com.focusforge.app.data.repository.PolicyRepository
import com.focusforge.app.data.repository.SessionRepository
import com.focusforge.app.data.repository.UserPreferencesRepository
import com.focusforge.app.enforcement.DnsVpnService
import com.focusforge.app.service.AppBlockerService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val userName: String = "",
    val isOnboardingCompleted: Boolean = true,
    val showBotGuide: Boolean = false,
    val todayDeepMinutes: Int = 0,
    val todayTotalMinutes: Int = 0,
    val todaySessions: List<Session> = emptyList(),
    val isBlockerActive: Boolean = false,
    val activeProfile: BlockingProfile = BlockingProfile.OFF,
    val isDnsActive: Boolean = false,
    val blockedAppCount: Int = 0,
    val isLoading: Boolean = false
) {
    val shallowMinutes: Int
        get() = (todayTotalMinutes - todayDeepMinutes).coerceAtLeast(0)

    val deepWorkRatio: Float
        get() = if (todayTotalMinutes > 0) todayDeepMinutes.toFloat() / todayTotalMinutes.toFloat() else 0f

    val greetingName: String
        get() = userName.ifBlank { "Scholar" }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    blockedAppRepository: BlockedAppRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val policyRepository: PolicyRepository
) : ViewModel() {

    private val _manualShowBotGuide = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        combine(
            sessionRepository.getTodayDeepMinutes(),
            sessionRepository.getTodayTotalMinutes(),
            sessionRepository.getTodaySessions()
        ) { deep, total, sessions -> Triple(deep, total, sessions) },
        combine(
            userPreferencesRepository.getUserName(),
            userPreferencesRepository.isOnboardingCompleted(),
            AppBlockerService.isServiceRunning,
            blockedAppRepository.getActiveBlockedApps()
        ) { name, onboard, running, blockedList ->
            Quad(name, onboard, running, blockedList.size)
        },
        combine(
            policyRepository.policySnapshot,
            DnsVpnService.isVpnRunning
        ) { snapshot, vpnRunning ->
            Pair(snapshot.activeProfile, vpnRunning)
        }
    ) { (deepMin, totalMin, sessions), (userName, onboardingCompleted, isBlockerRunning, blockedCount), (profile, vpnRunning) ->
        val isManual = _manualShowBotGuide.value
        val showGuide = isManual ?: (!onboardingCompleted)

        HomeUiState(
            userName = userName,
            isOnboardingCompleted = onboardingCompleted,
            showBotGuide = showGuide,
            todayDeepMinutes = deepMin,
            todayTotalMinutes = totalMin,
            todaySessions = sessions,
            isBlockerActive = isBlockerRunning,
            activeProfile = profile,
            isDnsActive = vpnRunning,
            blockedAppCount = blockedCount,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun saveUserName(name: String) {
        viewModelScope.launch {
            userPreferencesRepository.setUserName(name)
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            userPreferencesRepository.setOnboardingCompleted(true)
            _manualShowBotGuide.value = false
        }
    }

    fun openBotGuide() {
        _manualShowBotGuide.value = true
    }

    fun dismissBotGuide() {
        _manualShowBotGuide.value = false
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
