package com.focusforge.app.ui.timer

import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.ui.home.HomeUiState
import com.focusforge.app.ui.home.HomeViewModel
import org.junit.Assert.assertEquals
import org.junit.Test

class TimerAndHomeTest {

    @Test
    fun `HomeUiState computes shallowMinutes and deepWorkRatio accurately`() {
        val state = HomeUiState(
            todayDeepMinutes = 50,
            todayTotalMinutes = 75,
            past7DaysTotalMinutes = 300,
            past7DaysDeepMinutes = 220,
            past7DaysSessionCount = 6,
            todayScreenTimeMinutes = 90
        )
        assertEquals(25, state.shallowMinutes)
        assertEquals(50f / 75f, state.deepWorkRatio, 0.001f)
        assertEquals(300, state.past7DaysTotalMinutes)
        assertEquals(90, state.todayScreenTimeMinutes)
    }

    @Test
    fun `TimerUiState custom duration defaults to 25m`() {
        val ui = TimerUiState()
        assertEquals(25, ui.durationMinutes)
        assertEquals("25", ui.customDurationInput)
        assertEquals(SessionType.DEEP, ui.sessionType)
    }

    @Test
    fun `Session domain model preserves rating and duration`() {
        val session = Session(
            subject = "Algorithms",
            topic = "Graphs",
            startTime = 1_700_000_000_000L,
            durationMin = 42,
            focusRating = 5,
            type = SessionType.DEEP
        )
        assertEquals(42, session.durationMin)
        assertEquals(5, session.focusRating)
    }

    @Test
    fun `build7DayStats aggregates deep and shallow minutes per day`() {
        val now = 1_700_000_000_000L
        val oneDayMs = 24 * 60 * 60 * 1000L
        val sessions = listOf(
            Session(subject = "Math", topic = "Calc", startTime = now - 1000L, durationMin = 40, focusRating = 5, type = SessionType.DEEP),
            Session(subject = "Email", topic = "Inbox", startTime = now - 2000L, durationMin = 15, focusRating = 3, type = SessionType.SHALLOW),
            Session(subject = "Physics", topic = "Optics", startTime = now - 2 * oneDayMs, durationMin = 60, focusRating = 4, type = SessionType.DEEP)
        )
        val stats = HomeViewModel.build7DayStats(sessions, nowMillis = now)
        assertEquals(7, stats.size)
        assertEquals("Today", stats.last().shortLabel)
        assertEquals(40, stats.last().deepMinutes)
        assertEquals(15, stats.last().shallowMinutes)
        assertEquals(2, stats.last().sessionCount)
        assertEquals(60, stats[4].deepMinutes)
    }
}
