package com.focusforge.app.ui.timer

import android.content.Context
import android.content.SharedPreferences
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
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import kotlin.math.ceil

data class PendingJourney(
    val subject: String,
    val topic: String,
    val startTimeMillis: Long,
    val durationMin: Int,
    val durationSeconds: Int = durationMin * 60,
    val sessionType: SessionType,
    val note: String = ""
)

object PendingJourneyStorage {
    private const val PREFS_NAME = "focusforge_pending_journey"
    private const val KEY_LIST_JSON = "pending_journeys_json"
    private const val KEY_LAST_HANDLED = "last_handled_start_time"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadAll(prefs: SharedPreferences): List<PendingJourney> {
        val result = mutableListOf<PendingJourney>()
        val rawJson = prefs.getString(KEY_LIST_JSON, null)
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(rawJson)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val subject = obj.optString("subject", "General Focus").ifBlank { "General Focus" }
                    val topic = obj.optString("topic", "")
                    val startTime = obj.optLong("startTimeMillis", 0L)
                    val durationMin = obj.optInt("durationMin", 25).coerceAtLeast(1)
                    val durationSeconds = obj.optInt("durationSeconds", durationMin * 60).coerceAtLeast(1)
                    val typeName = obj.optString("sessionType", SessionType.DEEP.name)
                    val sessionType = runCatching { SessionType.valueOf(typeName) }.getOrDefault(SessionType.DEEP)
                    val note = obj.optString("note", "")
                    result.add(PendingJourney(subject, topic, startTime, durationMin, durationSeconds, sessionType, note))
                }
            }
        }
        // Migrate legacy single-item format if present
        if (prefs.getBoolean("has_pending", false)) {
            val subject = (prefs.getString("subject", "General Focus") ?: "General Focus").ifBlank { "General Focus" }
            val topic = prefs.getString("topic", "") ?: ""
            val startTime = prefs.getLong("startTimeMillis", 0L)
            val durationMin = prefs.getInt("durationMin", 25).coerceAtLeast(1)
            val durationSeconds = prefs.getInt("durationSeconds", durationMin * 60).coerceAtLeast(1)
            val typeName = prefs.getString("sessionType", SessionType.DEEP.name) ?: SessionType.DEEP.name
            val note = prefs.getString("note", "") ?: ""
            val sessionType = runCatching { SessionType.valueOf(typeName) }.getOrDefault(SessionType.DEEP)
            val legacy = PendingJourney(subject, topic, startTime, durationMin, durationSeconds, sessionType, note)
            if (result.none { it.startTimeMillis == legacy.startTimeMillis && legacy.startTimeMillis > 0L }) {
                result.add(0, legacy)
            }
            val editor = prefs.edit()
                .remove("has_pending")
                .remove("subject")
                .remove("topic")
                .remove("startTimeMillis")
                .remove("durationMin")
                .remove("durationSeconds")
                .remove("sessionType")
                .remove("note")
            writeList(editor, result)
            editor.apply()
        }
        return result
    }

    fun addOrUpdate(
        prefs: SharedPreferences,
        item: PendingJourney,
        lastHandledStartTime: Long? = null
    ): List<PendingJourney> {
        val current = loadAll(prefs).toMutableList()
        current.removeAll { it.startTimeMillis == item.startTimeMillis && item.startTimeMillis > 0L }
        current.add(0, item)
        val editor = prefs.edit()
        writeList(editor, current)
        if (lastHandledStartTime != null) {
            editor.putLong(KEY_LAST_HANDLED, lastHandledStartTime)
        }
        editor.apply()
        return current
    }

    fun remove(
        prefs: SharedPreferences,
        startTimeMillis: Long,
        lastHandledStartTime: Long? = null
    ): List<PendingJourney> {
        val current = loadAll(prefs).toMutableList()
        current.removeAll { it.startTimeMillis == startTimeMillis }
        val editor = prefs.edit()
        writeList(editor, current)
        if (lastHandledStartTime != null) {
            editor.putLong(KEY_LAST_HANDLED, lastHandledStartTime)
        }
        editor.apply()
        return current
    }

    private fun writeList(editor: SharedPreferences.Editor, list: List<PendingJourney>) {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject().apply {
                    put("subject", item.subject)
                    put("topic", item.topic)
                    put("startTimeMillis", item.startTimeMillis)
                    put("durationMin", item.durationMin)
                    put("durationSeconds", item.durationSeconds)
                    put("sessionType", item.sessionType.name)
                    put("note", item.note)
                }
            )
        }
        editor.putString(KEY_LIST_JSON, arr.toString())
    }
}

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
    val completedDurationSeconds: Int = 25 * 60,
    val completedSessionType: SessionType = SessionType.DEEP,
    val pendingJourneys: List<PendingJourney> = emptyList(),
    val activePendingJourney: PendingJourney? = null
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
    val completedDurationSeconds: Int = 25 * 60,
    val completedSessionType: SessionType = SessionType.DEEP,
    val pendingJourneys: List<PendingJourney> = emptyList(),
    val activePendingJourney: PendingJourney? = null
)

@HiltViewModel
class TimerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val sessionRepository: SessionRepository
) : ViewModel() {

    private val prefs = PendingJourneyStorage.prefs(appContext)
    private val _formState = MutableStateFlow(FormState(pendingJourneys = PendingJourneyStorage.loadAll(prefs)))
    private var completedSessionDetails: TimerState? = null
    private var lastSavedStartTimeMillis: Long = -1L

    init {
        viewModelScope.launch {
            TimerService.timerState.collect { serviceState ->
                val handledInActivity = prefs.getLong("last_handled_start_time", -1L)
                if (serviceState.status == TimerStatus.IDLE && completedSessionDetails != null) {
                    // External completion activity already handled and reset the service
                    completedSessionDetails = null
                    _formState.value = _formState.value.copy(
                        showRatingDialog = false,
                        pendingJourneys = PendingJourneyStorage.loadAll(prefs)
                    )
                } else if (serviceState.status == TimerStatus.COMPLETED &&
                    serviceState.startTimeMillis > 0L &&
                    serviceState.startTimeMillis != lastSavedStartTimeMillis &&
                    serviceState.startTimeMillis != handledInActivity &&
                    !_formState.value.showRatingDialog
                ) {
                    completedSessionDetails = serviceState
                    val completedMin = maxOf(1, serviceState.totalSeconds / 60)
                    _formState.value = _formState.value.copy(
                        showRatingDialog = true,
                        activePendingJourney = null,
                        completedSubject = serviceState.subject.ifBlank { "General Focus" },
                        completedDurationMin = completedMin,
                        completedDurationSeconds = serviceState.totalSeconds.coerceAtLeast(1),
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
            completedDurationSeconds = form.completedDurationSeconds,
            completedSessionType = form.completedSessionType,
            pendingJourneys = form.pendingJourneys,
            activePendingJourney = form.activePendingJourney
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

    fun stopAndSaveSession(context: Context, rating: Int = 4, reflectionNote: String = "") {
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
        val remaining = PendingJourneyStorage.remove(prefs, snapshot.startTimeMillis)
        _formState.value = _formState.value.copy(
            showRatingDialog = false,
            pendingJourneys = remaining,
            activePendingJourney = null
        )
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
                    topic = combineTopicAndNote(snapshot.topic, reflectionNote),
                    startTime = startTime,
                    durationMin = durationMin,
                    focusRating = rating.coerceIn(1, 5),
                    type = snapshot.sessionType
                )
            )
        }
    }

    fun stopAndSaveForLater(context: Context, reflectionNote: String = "") {
        val snapshot = TimerService.timerState.value
        TimerService.stopTimer(context)

        val elapsedSec = (snapshot.totalSeconds - snapshot.remainingSeconds).coerceAtLeast(1)
        if (snapshot.status != TimerStatus.IDLE) {
            val durationMin = maxOf(1, ceil(elapsedSec / 60.0).toInt())
            val startTime = if (snapshot.startTimeMillis > 0L) {
                snapshot.startTimeMillis
            } else {
                System.currentTimeMillis() - (durationMin * 60 * 1000L)
            }
            val pending = PendingJourney(
                subject = snapshot.subject.ifBlank { "General Focus" },
                topic = snapshot.topic,
                startTimeMillis = startTime,
                durationMin = durationMin,
                durationSeconds = elapsedSec,
                sessionType = snapshot.sessionType,
                note = reflectionNote.trim()
            )
            val updated = PendingJourneyStorage.addOrUpdate(prefs, pending)
            lastSavedStartTimeMillis = snapshot.startTimeMillis
            _formState.value = _formState.value.copy(
                showRatingDialog = false,
                pendingJourneys = updated,
                activePendingJourney = null
            )
        }
        completedSessionDetails = null
        TimerService.resetState()
    }

    fun saveCompletedSession(rating: Int, reflectionNote: String = "") {
        val pending = _formState.value.activePendingJourney
        val sessionToSave = completedSessionDetails ?: TimerService.timerState.value

        if (pending != null && completedSessionDetails == null && sessionToSave.status == TimerStatus.IDLE) {
            val remaining = PendingJourneyStorage.remove(prefs, pending.startTimeMillis)
            _formState.value = _formState.value.copy(
                showRatingDialog = false,
                pendingJourneys = remaining,
                activePendingJourney = null
            )
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
            _formState.value = _formState.value.copy(showRatingDialog = false, activePendingJourney = null)
            completedSessionDetails = null
            TimerService.resetState()
            return
        }

        lastSavedStartTimeMillis = sessionToSave.startTimeMillis
        val remaining = PendingJourneyStorage.remove(prefs, sessionToSave.startTimeMillis)
        _formState.value = _formState.value.copy(
            showRatingDialog = false,
            pendingJourneys = remaining,
            activePendingJourney = null
        )
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
        val activePending = _formState.value.activePendingJourney
        val sessionToDefer = completedSessionDetails ?: TimerService.timerState.value
        if (activePending != null && completedSessionDetails == null && sessionToDefer.status == TimerStatus.IDLE) {
            val updatedItem = activePending.copy(note = reflectionNote.trim())
            val updatedList = PendingJourneyStorage.addOrUpdate(prefs, updatedItem)
            _formState.value = _formState.value.copy(
                showRatingDialog = false,
                pendingJourneys = updatedList,
                activePendingJourney = null
            )
            return
        }
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
                durationSeconds = sessionToDefer.totalSeconds.coerceAtLeast(1),
                sessionType = sessionToDefer.sessionType,
                note = reflectionNote.trim()
            )
            val updatedList = PendingJourneyStorage.addOrUpdate(prefs, pending)
            lastSavedStartTimeMillis = sessionToDefer.startTimeMillis
            _formState.value = _formState.value.copy(
                showRatingDialog = false,
                pendingJourneys = updatedList,
                activePendingJourney = null
            )
        } else {
            _formState.value = _formState.value.copy(showRatingDialog = false, activePendingJourney = null)
        }
        completedSessionDetails = null
        TimerService.resetState()
    }

    fun openPendingJourney(pending: PendingJourney) {
        _formState.value = _formState.value.copy(
            showRatingDialog = true,
            activePendingJourney = pending,
            completedSubject = pending.subject,
            completedDurationMin = pending.durationMin,
            completedDurationSeconds = pending.durationSeconds,
            completedSessionType = pending.sessionType
        )
    }

    fun refreshPendingJourney() {
        val handledInActivity = prefs.getLong("last_handled_start_time", -1L)
        val shouldDismiss = completedSessionDetails != null &&
            (completedSessionDetails?.startTimeMillis == handledInActivity ||
                TimerService.timerState.value.status == TimerStatus.IDLE)
        if (shouldDismiss) {
            completedSessionDetails = null
        }
        _formState.value = _formState.value.copy(
            showRatingDialog = if (shouldDismiss) false else _formState.value.showRatingDialog,
            pendingJourneys = PendingJourneyStorage.loadAll(prefs)
        )
    }

    fun dismissRatingDialog() {
        val activePending = _formState.value.activePendingJourney
        if (activePending != null && completedSessionDetails == null) {
            val remaining = PendingJourneyStorage.remove(prefs, activePending.startTimeMillis)
            _formState.value = _formState.value.copy(
                showRatingDialog = false,
                pendingJourneys = remaining,
                activePendingJourney = null
            )
            return
        }
        lastSavedStartTimeMillis = completedSessionDetails?.startTimeMillis ?: -1L
        _formState.value = _formState.value.copy(showRatingDialog = false, activePendingJourney = null)
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
}
