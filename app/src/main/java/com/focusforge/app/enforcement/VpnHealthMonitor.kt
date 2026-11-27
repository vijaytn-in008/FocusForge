package com.focusforge.app.enforcement

/**
 * Pure state machine monitoring upstream DNS health for fail-open behavior.
 *
 * If upstream errors exceed [errorThreshold] within [windowMs] without an intervening
 * success, transitions to [HealthState.DEGRADED_FAIL_OPEN].
 */
class VpnHealthMonitor(
    private val errorThreshold: Int = 5,
    private val windowMs: Long = 15_000L
) {
    enum class HealthState {
        HEALTHY,
        DEGRADED_FAIL_OPEN
    }

    private val errorTimestamps = ArrayDeque<Long>()
    var state: HealthState = HealthState.HEALTHY
        private set

    var totalFailOpenCount: Int = 0
        private set

    @Synchronized
    fun recordSuccess() {
        errorTimestamps.clear()
        state = HealthState.HEALTHY
    }

    /**
     * Records an upstream DNS failure at [nowMillis].
     * Returns true if this failure tripped the fail-open threshold.
     */
    @Synchronized
    fun recordFailure(nowMillis: Long = System.currentTimeMillis()): Boolean {
        while (errorTimestamps.isNotEmpty() && (nowMillis - errorTimestamps.first()) > windowMs) {
            errorTimestamps.removeFirst()
        }
        errorTimestamps.addLast(nowMillis)

        if (errorTimestamps.size >= errorThreshold && state != HealthState.DEGRADED_FAIL_OPEN) {
            state = HealthState.DEGRADED_FAIL_OPEN
            totalFailOpenCount++
            return true
        }
        return false
    }

    @Synchronized
    fun reset() {
        errorTimestamps.clear()
        state = HealthState.HEALTHY
    }
}
