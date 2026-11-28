package com.focusforge.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.data.repository.SessionRepository
import com.focusforge.app.service.TimerService
import com.focusforge.app.ui.theme.FocusForgeTheme
import com.focusforge.app.ui.timer.PendingJourney
import com.focusforge.app.ui.timer.PendingJourneyStorage
import com.focusforge.app.ui.timer.TimerCompletionFullScreen
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Full-screen pop-up launched when a focus session finishes, matching the
 * full-screen interceptor pattern used during focus enforcement.
 */
@AndroidEntryPoint
class TimerCompletionActivity : ComponentActivity() {

    @Inject
    lateinit var sessionRepository: SessionRepository

    companion object {
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_START_TIME = "extra_start_time"
        const val EXTRA_DURATION_MIN = "extra_duration_min"
        const val EXTRA_DURATION_SECONDS = "extra_duration_seconds"
        const val EXTRA_SESSION_TYPE = "extra_session_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val subject = intent.getStringExtra(EXTRA_SUBJECT)?.ifBlank { "General Focus" } ?: "General Focus"
        val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
        val durationMin = intent.getIntExtra(EXTRA_DURATION_MIN, 25).coerceAtLeast(1)
        val durationSeconds = intent.getIntExtra(EXTRA_DURATION_SECONDS, durationMin * 60).coerceAtLeast(1)
        val startTimeMillis = intent.getLongExtra(
            EXTRA_START_TIME,
            System.currentTimeMillis() - durationMin * 60_000L
        )
        val sessionTypeName = intent.getStringExtra(EXTRA_SESSION_TYPE) ?: SessionType.DEEP.name
        val sessionType = runCatching { SessionType.valueOf(sessionTypeName) }.getOrDefault(SessionType.DEEP)

        setContent {
            FocusForgeTheme {
                var selectedRating by remember { mutableIntStateOf(5) }
                TimerCompletionFullScreen(
                    subject = subject,
                    durationSeconds = durationSeconds,
                    sessionType = sessionType,
                    initialNote = "",
                    currentRating = selectedRating,
                    onRatingSelected = { selectedRating = it.coerceIn(1, 5) },
                    onSaveNow = { note ->
                        val combinedTopic = when {
                            topic.isNotBlank() && note.isNotBlank() -> "${topic.trim()} — ${note.trim()}"
                            note.isNotBlank() -> note.trim()
                            else -> topic.trim()
                        }
                        PendingJourneyStorage.remove(
                            prefs = PendingJourneyStorage.prefs(this@TimerCompletionActivity),
                            startTimeMillis = startTimeMillis,
                            lastHandledStartTime = startTimeMillis
                        )
                        TimerService.resetState()
                        lifecycleScope.launch {
                            sessionRepository.saveSession(
                                Session(
                                    subject = subject,
                                    topic = combinedTopic,
                                    startTime = startTimeMillis,
                                    durationMin = durationMin,
                                    focusRating = selectedRating.coerceIn(1, 5),
                                    type = sessionType
                                )
                            )
                            finish()
                        }
                    },
                    onSaveLater = { note ->
                        PendingJourneyStorage.addOrUpdate(
                            prefs = PendingJourneyStorage.prefs(this@TimerCompletionActivity),
                            item = PendingJourney(
                                subject = subject,
                                topic = topic,
                                startTimeMillis = startTimeMillis,
                                durationMin = durationMin,
                                durationSeconds = durationSeconds,
                                sessionType = sessionType,
                                note = note.trim()
                            ),
                            lastHandledStartTime = startTimeMillis
                        )
                        TimerService.resetState()
                        finish()
                    },
                    onDiscard = {
                        PendingJourneyStorage.remove(
                            prefs = PendingJourneyStorage.prefs(this@TimerCompletionActivity),
                            startTimeMillis = startTimeMillis,
                            lastHandledStartTime = startTimeMillis
                        )
                        TimerService.resetState()
                        finish()
                    }
                )
            }
        }
    }
}
