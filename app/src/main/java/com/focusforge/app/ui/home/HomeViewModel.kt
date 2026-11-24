package com.focusforge.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.repository.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HomeUiState(
    val todayDeepMinutes: Int = 0,
    val todayTotalMinutes: Int = 0,
    val todaySessions: List<Session> = emptyList(),
    val isLoading: Boolean = false
) {
    val shallowMinutes: Int
        get() = (todayTotalMinutes - todayDeepMinutes).coerceAtLeast(0)

    val deepWorkRatio: Float
        get() = if (todayTotalMinutes > 0) todayDeepMinutes.toFloat() / todayTotalMinutes.toFloat() else 0f
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    sessionRepository: SessionRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        sessionRepository.getTodayDeepMinutes(),
        sessionRepository.getTodayTotalMinutes(),
        sessionRepository.getTodaySessions()
    ) { deepMin, totalMin, sessions ->
        HomeUiState(
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
}
