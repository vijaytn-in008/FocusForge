package com.focusforge.app.ui.timer

import com.focusforge.app.data.model.Session
import com.focusforge.app.data.model.SessionType
import com.focusforge.app.ui.home.HomeUiState
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
}
