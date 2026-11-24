package com.focusforge.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.repository.SessionRepository
import com.focusforge.app.data.repository.UserPreferencesRepository
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
    private val userPreferencesRepository: UserPreferencesRepository
) : ViewModel() {

    private val _manualShowBotGuide = MutableStateFlow<Boolean?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        sessionRepository.getTodayDeepMinutes(),
        sessionRepository.getTodayTotalMinutes(),
        sessionRepository.getTodaySessions(),
        userPreferencesRepository.getUserName(),
        userPreferencesRepository.isOnboardingCompleted()
    ) { deepMin, totalMin, sessions, userName, onboardingCompleted ->
        // If the user hasn't completed onboarding, automatically show the bot guide
        val isManual = _manualShowBotGuide.value
        val showGuide = isManual ?: (!onboardingCompleted)

        HomeUiState(
            userName = userName,
            isOnboardingCompleted = onboardingCompleted,
            showBotGuide = showGuide,
            todayDeepMinutes = deepMin,
            todayTotalMinutes = totalMin,
            todaySessions = sessions,
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

    fun closeBotGuide() {
        _manualShowBotGuide.value = false
    }
}
