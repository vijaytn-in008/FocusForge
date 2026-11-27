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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.ceil

data class PendingJourney(
    val subject: String,
    val topic: String,
    val startTimeMillis: Long,
    val durationMin: Int,
    val sessionType: SessionType,
    val note: String = ""
)

data class TimerUiState(
    val subjectInput: String = "General Focus",
    val topicInput: String = "",
    val durationMinutes: Int = 25,
    val durationSecondsTotal: Int = 25 * 60,
    val customDurationInput: String = "25",
    val sessionType: SessionType = SessionType.DEEP,
    val timerServiceState: TimerState = TimerState(),
    val showRatingDialog: Boolean = false,
    val selectedRating: Int = 5,
    val completedSubject: String = "General Focus",
    val completedDurationMin: Int = 25,
    val completedSessionType: SessionType = SessionType.DEEP,
    val pendingJourney: PendingJourney? = null
)

private data class FormState(
    val subject: String = "General Focus",
    val topic: String = "",
    val durationMinutes: Int = 25,
    val durationSecondsTotal: Int = 25 * 60,
    val customDurationInput: String = "25",
    val sessionType: SessionType = SessionType.DEEP,
    val showRatingDialog: Boolean = false,
    val selectedRating: Int = 5,
    val completedSubject: String = "General Focus",
    val completedDurationMin: Int = 25,
    val completedSessionType: SessionType = SessionType.DEEP,
    val pendingJourney: PendingJourney? = null
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val prefs = appContext.getSharedPreferences("focusforge_pending_journey", Context.MODE_PRIVATE)
    private val _formState = MutableStateFlow(FormState(pendingJourney = loadPendingJourney()))
    private var completedSessionDetails: TimerState? = null
    private var lastSavedStartTimeMillis: Long = -1L

    init {
        viewModelScope.launch {
            TimerService.timerState.collect { serviceState ->
                if (serviceState.status == TimerStatus.COMPLETED &&
                    serviceState.startTimeMillis > 0L &&
                    serviceState.startTimeMillis != lastSavedStartTimeMillis &&
                    !_formState.value.showRatingDialog
                ) {
                    completedSessionDetails = serviceState
                    val completedMin = maxOf(1, serviceState.totalSeconds / 60)
                    _formState.value = _formState.value.copy(
                        showRatingDialog = true,
                        completedSubject = serviceState.subject.ifBlank { "General Focus" },
                        completedDurationMin = completedMin,
                        completedSessionType = serviceState.sessionType
                    )
                }
            }
        }
    }

    val uiState: StateFlow<TimerUiState> = combine(
        _formState,
        TimerService.timerState
    ) { form, serviceState ->
        TimerUiState(
            subjectInput = form.subject,
            topicInput = form.topic,
            durationMinutes = form.durationMinutes,
            durationSecondsTotal = form.durationSecondsTotal,
            customDurationInput = form.customDurationInput,
            sessionType = form.sessionType,
            timerServiceState = serviceState,
            showRatingDialog = form.showRatingDialog,
            selectedRating = form.selectedRating,
            completedSubject = form.completedSubject,
            completedDurationMin = form.completedDurationMin,
            completedSessionType = form.completedSessionType,
            pendingJourney = form.pendingJourney
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
        val clamped = minutes.coerceIn(1, 720)
        _formState.value = _formState.value.copy(
            durationMinutes = clamped,
            durationSecondsTotal = clamped * 60,
            customDurationInput = clamped.toString()
        )
    }

    fun onTotalSecondsChanged(totalSeconds: Int) {
        val clamped = totalSeconds.coerceIn(1, 12 * 3600 + 59 * 60 + 59)
        _formState.value = _formState.value.copy(
            durationSecondsTotal = clamped,
            durationMinutes = maxOf(1, (clamped + 59) / 60),
            customDurationInput = maxOf(1, clamped / 60).toString()
        )
    }

    fun onCustomDurationInputChanged(raw: String) {
        val digitsOnly = raw.filter { it.isDigit() }.take(3)
        val parsed = digitsOnly.toIntOrNull()
        _formState.value = if (parsed != null && parsed > 0) {
            _formState.value.copy(
                customDurationInput = digitsOnly,
                durationMinutes = parsed.coerceIn(1, 240)
            )
        } else {
            _formState.value.copy(customDurationInput = digitsOnly)
        }
    }

    fun onSessionTypeChanged(type: SessionType) {
        _formState.value = _formState.value.copy(sessionType = type)
    }

    fun onRatingChanged(rating: Int) {
        _formState.value = _formState.value.copy(selectedRating = rating.coerceIn(1, 5))
    }

    fun startTimer(context: Context) {
        val form = _formState.value
        val durationSec = form.durationSecondsTotal.coerceAtLeast(1)
        lastSavedStartTimeMillis = -1L
        completedSessionDetails = null
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
        val snapshot = TimerService.timerState.value
        if (shouldSavePartial && snapshot.status != TimerStatus.IDLE) {
            stopAndSaveSession(context, _formState.value.selectedRating)
            return
        }
        TimerService.stopTimer(context)
        completedSessionDetails = null
        _formState.value = _formState.value.copy(showRatingDialog = false)
        TimerService.resetState()
    }

    fun stopAndSaveSession(context: Context, rating: Int = 4) {
        // Capture state BEFORE stopping the service (which resets TimerState)
        val snapshot = TimerService.timerState.value
        TimerService.stopTimer(context)

        if (snapshot.status == TimerStatus.IDLE ||
            (snapshot.startTimeMillis > 0L && snapshot.startTimeMillis == lastSavedStartTimeMillis)
        ) {
            _formState.value = _formState.value.copy(showRatingDialog = false)
            TimerService.resetState()
            return
        }

        val elapsedSec = (snapshot.totalSeconds - snapshot.remainingSeconds).coerceAtLeast(0)
        if (elapsedSec <= 0) {
            _formState.value = _formState.value.copy(showRatingDialog = false)
            TimerService.resetState()
            return
        }

        lastSavedStartTimeMillis = snapshot.startTimeMillis
        completedSessionDetails = null
        _formState.value = _formState.value.copy(showRatingDialog = false)
        TimerService.resetState()

        viewModelScope.launch {
            val durationMin = maxOf(1, ceil(elapsedSec / 60.0).toInt())
            val startTime = if (snapshot.startTimeMillis > 0L) {
                snapshot.startTimeMillis
            } else {
                System.currentTimeMillis() - (durationMin * 60 * 1000L)
            }
            sessionRepository.saveSession(
                Session(
                    subject = snapshot.subject.ifBlank { "General Focus" },
                    topic = snapshot.topic,
                    startTime = startTime,
                    durationMin = durationMin,
                    focusRating = rating.coerceIn(1, 5),
                    type = snapshot.sessionType
                )
            )
        }
    }

    fun saveCompletedSession(rating: Int, reflectionNote: String = "") {
        val pending = _formState.value.pendingJourney
        val sessionToSave = completedSessionDetails ?: TimerService.timerState.value

        if (pending != null && completedSessionDetails == null && sessionToSave.status == TimerStatus.IDLE) {
            clearPendingJourney()
            _formState.value = _formState.value.copy(showRatingDialog = false, pendingJourney = null)
            viewModelScope.launch {
                sessionRepository.saveSession(
                    Session(
                        subject = pending.subject.ifBlank { "General Focus" },
                        topic = combineTopicAndNote(pending.topic, reflectionNote.ifBlank { pending.note }),
                        startTime = pending.startTimeMillis,
                        durationMin = pending.durationMin,
                        focusRating = rating.coerceIn(1, 5),
                        type = pending.sessionType
                    )
                )
            }
            return
        }

        if (sessionToSave.status == TimerStatus.IDLE ||
            (sessionToSave.startTimeMillis > 0L && sessionToSave.startTimeMillis == lastSavedStartTimeMillis)
        ) {
            _formState.value = _formState.value.copy(showRatingDialog = false)
            completedSessionDetails = null
            TimerService.resetState()
            return
        }

        lastSavedStartTimeMillis = sessionToSave.startTimeMillis
        clearPendingJourney()
        _formState.value = _formState.value.copy(showRatingDialog = false, pendingJourney = null)
        completedSessionDetails = null
        TimerService.resetState()

        viewModelScope.launch {
            val elapsedSec = (sessionToSave.totalSeconds - sessionToSave.remainingSeconds).coerceAtLeast(0)
            val durationMin = if (sessionToSave.status == TimerStatus.COMPLETED || sessionToSave.remainingSeconds == 0) {
                maxOf(1, sessionToSave.totalSeconds / 60)
            } else {
                maxOf(1, ceil(elapsedSec / 60.0).toInt())
            }
            val startTime = if (sessionToSave.startTimeMillis > 0L) {
                sessionToSave.startTimeMillis
            } else {
                System.currentTimeMillis() - (durationMin * 60 * 1000L)
            }
            sessionRepository.saveSession(
                Session(
                    subject = sessionToSave.subject.ifBlank { "General Focus" },
                    topic = combineTopicAndNote(sessionToSave.topic, reflectionNote),
                    startTime = startTime,
                    durationMin = durationMin,
                    focusRating = rating.coerceIn(1, 5),
                    type = sessionToSave.sessionType
                )
            )
        }
    }

    fun saveForLater(reflectionNote: String = "") {
        val sessionToDefer = completedSessionDetails ?: TimerService.timerState.value
        if (sessionToDefer.status != TimerStatus.IDLE) {
            val elapsedSec = (sessionToDefer.totalSeconds - sessionToDefer.remainingSeconds).coerceAtLeast(0)
            val durationMin = if (sessionToDefer.status == TimerStatus.COMPLETED || sessionToDefer.remainingSeconds == 0) {
                maxOf(1, sessionToDefer.totalSeconds / 60)
            } else {
                maxOf(1, ceil(elapsedSec / 60.0).toInt())
            }
            val startTime = if (sessionToDefer.startTimeMillis > 0L) {
                sessionToDefer.startTimeMillis
            } else {
                System.currentTimeMillis() - (durationMin * 60 * 1000L)
            }
            val pending = PendingJourney(
                subject = sessionToDefer.subject.ifBlank { "General Focus" },
                topic = sessionToDefer.topic,
                startTimeMillis = startTime,
                durationMin = durationMin,
                sessionType = sessionToDefer.sessionType,
                note = reflectionNote.trim()
            )
            persistPendingJourney(pending)
            lastSavedStartTimeMillis = sessionToDefer.startTimeMillis
            _formState.value = _formState.value.copy(
                showRatingDialog = false,
                pendingJourney = pending
            )
        } else {
            _formState.value = _formState.value.copy(showRatingDialog = false)
        }
        completedSessionDetails = null
        TimerService.resetState()
    }

    fun openPendingJourney() {
        val pending = _formState.value.pendingJourney ?: return
        _formState.value = _formState.value.copy(
            showRatingDialog = true,
            completedSubject = pending.subject,
            completedDurationMin = pending.durationMin,
            completedSessionType = pending.sessionType
        )
    }

    fun dismissRatingDialog() {
        lastSavedStartTimeMillis = completedSessionDetails?.startTimeMillis ?: -1L
        clearPendingJourney()
        _formState.value = _formState.value.copy(showRatingDialog = false, pendingJourney = null)
        completedSessionDetails = null
        TimerService.resetState()
    }

    private fun combineTopicAndNote(topic: String, note: String): String {
        val cleanTopic = topic.trim()
        val cleanNote = note.trim()
        return when {
            cleanTopic.isNotEmpty() && cleanNote.isNotEmpty() -> "$cleanTopic — $cleanNote"
            cleanNote.isNotEmpty() -> cleanNote
            else -> cleanTopic
        }
    }

    private fun persistPendingJourney(pending: PendingJourney) {
        prefs.edit()
            .putBoolean("has_pending", true)
            .putString("subject", pending.subject)
            .putString("topic", pending.topic)
            .putLong("startTimeMillis", pending.startTimeMillis)
            .putInt("durationMin", pending.durationMin)
            .putString("sessionType", pending.sessionType.name)
            .putString("note", pending.note)
            .apply()
    }

    private fun clearPendingJourney() {
        prefs.edit().clear().apply()
    }

    private fun loadPendingJourney(): PendingJourney? {
        if (!prefs.getBoolean("has_pending", false)) return null
        val subject = prefs.getString("subject", "General Focus") ?: "General Focus"
        val topic = prefs.getString("topic", "") ?: ""
        val startTime = prefs.getLong("startTimeMillis", 0L)
        val durationMin = prefs.getInt("durationMin", 25)
        val typeName = prefs.getString("sessionType", SessionType.DEEP.name) ?: SessionType.DEEP.name
        val note = prefs.getString("note", "") ?: ""
        val sessionType = runCatching { SessionType.valueOf(typeName) }.getOrDefault(SessionType.DEEP)
        return PendingJourney(subject, topic, startTime, durationMin, sessionType, note)
    }
}
