package com.focusforge.app.enforcement

import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.PolicySnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WatcherGateTest {

    @Test
    fun `shouldPoll returns false when screen is off regardless of policy`() {
        val activeStrict = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            blockedApps = setOf("com.example.distraction")
        )
        assertFalse(WatcherGateLogic.shouldPoll(isScreenInteractive = false, snapshot = activeStrict))
    }

    @Test
    fun `shouldPoll returns false when profile is OFF`() {
        val offSnapshot = PolicySnapshot(
            activeProfile = BlockingProfile.OFF,
            blockedApps = setOf("com.example.distraction")
        )
        assertFalse(WatcherGateLogic.shouldPoll(isScreenInteractive = true, snapshot = offSnapshot))
    }

    @Test
    fun `shouldPoll returns false when blocked app sets are empty`() {
        val emptySnapshot = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            blockedApps = emptySet(),
            privateBlockedApps = emptySet()
        )
        assertFalse(WatcherGateLogic.shouldPoll(isScreenInteractive = true, snapshot = emptySnapshot))
    }

    @Test
    fun `shouldPoll returns true when screen on, RELAXED or STRICT, and blocked apps non-empty`() {
        val relaxed = PolicySnapshot(
            activeProfile = BlockingProfile.RELAXED,
            blockedApps = setOf("com.example.app")
        )
        val strictPrivate = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            privateBlockedApps = setOf("com.example.private")
        )
        assertTrue(WatcherGateLogic.shouldPoll(isScreenInteractive = true, snapshot = relaxed))
        assertTrue(WatcherGateLogic.shouldPoll(isScreenInteractive = true, snapshot = strictPrivate))
    }

    @Test
    fun `shouldPoll respects temporary pauseUntil`() {
        val now = 5_000_000L
        val paused = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            blockedApps = setOf("com.example.app"),
            pauseUntil = now + 300_000L
        )
        assertFalse(WatcherGateLogic.shouldPoll(isScreenInteractive = true, snapshot = paused, nowMillis = now))
        assertTrue(WatcherGateLogic.shouldPoll(isScreenInteractive = true, snapshot = paused, nowMillis = now + 300_001L))
    }
}
