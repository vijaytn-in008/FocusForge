package com.focusforge.app.enforcement

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnHealthMonitorTest {

    @Test
    fun `trips fail-open when error threshold exceeded within window`() {
        val monitor = VpnHealthMonitor(errorThreshold = 5, windowMs = 15_000L)
        val t0 = 1_000_000L

        repeat(4) { i ->
            assertFalse(monitor.recordFailure(t0 + i * 1_000L))
            assertEquals(VpnHealthMonitor.HealthState.HEALTHY, monitor.state)
        }

        // 5th error within window trips fail-open
        assertTrue(monitor.recordFailure(t0 + 4_000L))
        assertEquals(VpnHealthMonitor.HealthState.DEGRADED_FAIL_OPEN, monitor.state)
        assertEquals(1, monitor.totalFailOpenCount)
    }

    @Test
    fun `errors outside sliding window expire and success resets state`() {
        val monitor = VpnHealthMonitor(errorThreshold = 3, windowMs = 10_000L)
        val t0 = 1_000_000L

        assertFalse(monitor.recordFailure(t0))
        assertFalse(monitor.recordFailure(t0 + 2_000L))
        // 12s later -> first two errors expired
        assertFalse(monitor.recordFailure(t0 + 13_000L))
        assertEquals(VpnHealthMonitor.HealthState.HEALTHY, monitor.state)

        // Success clears window
        monitor.recordSuccess()
        assertFalse(monitor.recordFailure(t0 + 14_000L))
        assertFalse(monitor.recordFailure(t0 + 15_000L))
        assertTrue(monitor.recordFailure(t0 + 16_000L))

        monitor.recordSuccess()
        assertEquals(VpnHealthMonitor.HealthState.HEALTHY, monitor.state)
    }
}
