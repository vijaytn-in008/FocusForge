package com.focusforge.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {

    @Test
    fun testFormatSecondsToTime_underOneHour() {
        assertEquals("00:00", TimeUtils.formatSecondsToTime(0))
        assertEquals("00:05", TimeUtils.formatSecondsToTime(5))
        assertEquals("01:30", TimeUtils.formatSecondsToTime(90))
        assertEquals("25:00", TimeUtils.formatSecondsToTime(25 * 60))
        assertEquals("59:59", TimeUtils.formatSecondsToTime(3599))
    }

    @Test
    fun testFormatSecondsToTime_overOneHour() {
        assertEquals("01:00:00", TimeUtils.formatSecondsToTime(3600))
        assertEquals("01:30:15", TimeUtils.formatSecondsToTime(3600 + 1800 + 15))
    }

    @Test
    fun testFormatSecondsToTime_negativeCoercesToZero() {
        assertEquals("00:00", TimeUtils.formatSecondsToTime(-10))
    }

    @Test
    fun testFormatMinutesToDuration() {
        assertEquals("25m", TimeUtils.formatMinutesToDuration(25))
        assertEquals("1h", TimeUtils.formatMinutesToDuration(60))
        assertEquals("1h 30m", TimeUtils.formatMinutesToDuration(90))
        assertEquals("2h", TimeUtils.formatMinutesToDuration(120))
    }
}
