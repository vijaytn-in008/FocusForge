package com.focusforge.app.ui.timer

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.data.repository.SessionRepository
import com.focusforge.app.service.TimerService
import com.focusforge.app.service.TimerState
import com.focusforge.app.service.TimerStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimerUiState(
    val subjectInput: String = "General Focus",
    val topicInput: String = "",
    val durationMinutes: Int = 25,
    val sessionType: SessionType = SessionType.DEEP,
    val timerServiceState: TimerState = TimerState(),
    val showRatingDialog: Boolean = false,
    val selectedRating: Int = 5
)

private data class FormState(
    val subject: String = "General Focus",
    val topic: String = "",
    val durationMinutes: Int = 25,
    val sessionType: SessionType = SessionType.DEEP,
    val showRatingDialog: Boolean = false,
    val selectedRating: Int = 5
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val _formState = MutableStateFlow(FormState())

    // Store completed session details for rating
    private var completedSessionDetails: TimerState? = null

    val uiState: StateFlow<TimerUiState> = combine(
        _formState,
        TimerService.timerState
    ) { form, serviceState ->
        // Auto-show rating dialog when timer status transitions to COMPLETED
        if (serviceState.status == TimerStatus.COMPLETED && !form.showRatingDialog) {
            completedSessionDetails = serviceState
            _formState.value = _formState.value.copy(showRatingDialog = true)
        }

        TimerUiState(
            subjectInput = form.subject,
            topicInput = form.topic,
            durationMinutes = form.durationMinutes,
            sessionType = form.sessionType,
            timerServiceState = serviceState,
            showRatingDialog = form.showRatingDialog,
            selectedRating = form.selectedRating
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TimerUiState()
    )

    fun onSubjectChanged(newSubject: String) {
        _formState.value = _formState.value.copy(subject = newSubject)
    }

    fun onTopicChanged(newTopic: String) {
        _formState.value = _formState.value.copy(topic = newTopic)
    }

    fun onDurationChanged(minutes: Int) {
        _formState.value = _formState.value.copy(durationMinutes = minutes.coerceIn(1, 180))
    }

    fun onSessionTypeChanged(type: SessionType) {
        _formState.value = _formState.value.copy(sessionType = type)
    }

    fun onRatingChanged(rating: Int) {
        _formState.value = _formState.value.copy(selectedRating = rating.coerceIn(1, 5))
    }

    fun startTimer(context: Context) {
        val form = _formState.value
        val durationSec = form.durationMinutes * 60
        TimerService.startTimer(
            context = context,
            subject = form.subject.ifBlank { "General Focus" },
            topic = form.topic.trim(),
            durationSeconds = durationSec,
            sessionType = form.sessionType
        )
    }

    fun pauseTimer(context: Context) {
        TimerService.pauseTimer(context)
    }

    fun resumeTimer(context: Context) {
        TimerService.resumeTimer(context)
    }

    fun stopTimer(context: Context, shouldSavePartial: Boolean = false) {
        val current = TimerService.timerState.value
        TimerService.stopTimer(context)

        if (shouldSavePartial && current.status != TimerStatus.IDLE) {
            val elapsedSec = current.totalSeconds - current.remainingSeconds
            val elapsedMin = elapsedSec / 60
            if (elapsedMin >= 1) {
                completedSessionDetails = current
                _formState.value = _formState.value.copy(showRatingDialog = true)
                return
            }
        }
        TimerService.resetState()
    }

    fun saveCompletedSession(rating: Int) {
        viewModelScope.launch {
            val sessionToSave = completedSessionDetails ?: TimerService.timerState.value
            val elapsedSec = (sessionToSave.totalSeconds - sessionToSave.remainingSeconds).coerceAtLeast(60)
            val durationMin = (elapsedSec / 60).coerceAtLeast(1)

            val session = Session(
                subject = sessionToSave.subject.ifBlank { "General Focus" },
                topic = sessionToSave.topic,
                startTime = if (sessionToSave.startTimeMillis > 0) sessionToSave.startTimeMillis else System.currentTimeMillis() - (durationMin * 60 * 1000L),
                durationMin = durationMin,
                focusRating = rating,
                type = sessionToSave.sessionType
            )

            sessionRepository.saveSession(session)
            _formState.value = _formState.value.copy(showRatingDialog = false)
            completedSessionDetails = null
            TimerService.resetState()
        }
    }

    fun dismissRatingDialog() {
        _formState.value = _formState.value.copy(showRatingDialog = false)
        completedSessionDetails = null
        TimerService.resetState()
    }
}
