package com.focusforge.app.data.repository

import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.PolicyTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicySnapshotDerivationTest {

    @Test
    fun `deriveSnapshot covers every profile x tier combination`() {
        val allTiers = PolicyTier.entries

        for (profile in BlockingProfile.entries) {
            // 1. All tiers enabled
            val allOnPrefs = allTiers.associateWith { true }
            val allOnSnapshot = PolicyStoreImpl.deriveSnapshot(
                activeProfile = profile,
                tierPreferences = allOnPrefs
            )
            assertTrue(allOnSnapshot.enabledTiers.contains(PolicyTier.ADULT_CORE))
            assertTrue(allOnSnapshot.enabledTiers.contains(PolicyTier.ADULT_EXTENDED))
            assertTrue(allOnSnapshot.enabledTiers.contains(PolicyTier.DOH_ENDPOINTS))
            if (profile == BlockingProfile.STRICT) {
                assertTrue(
                    "DISTRACTION should be active in STRICT when enabled",
                    allOnSnapshot.enabledTiers.contains(PolicyTier.DISTRACTION)
                )
            } else {
                assertFalse(
                    "DISTRACTION must NOT be active in $profile",
                    allOnSnapshot.enabledTiers.contains(PolicyTier.DISTRACTION)
                )
            }

            // 2. All tiers disabled
            val allOffPrefs = allTiers.associateWith { false }
            val allOffSnapshot = PolicyStoreImpl.deriveSnapshot(
                activeProfile = profile,
                tierPreferences = allOffPrefs
            )
            assertTrue(allOffSnapshot.enabledTiers.isEmpty())

            // 3. Single tier enabled at a time
            for (singleTier in allTiers) {
                val singlePref = allTiers.associateWith { it == singleTier }
                val snapshot = PolicyStoreImpl.deriveSnapshot(
                    activeProfile = profile,
                    tierPreferences = singlePref
                )
                val shouldBePresent = when (singleTier) {
                    PolicyTier.DISTRACTION -> profile == BlockingProfile.STRICT
                    PolicyTier.ADULT_CORE,
                    PolicyTier.ADULT_EXTENDED,
                    PolicyTier.DOH_ENDPOINTS -> true
                }
                assertEquals(
                    "Profile=$profile, Tier=$singleTier",
                    shouldBePresent,
                    snapshot.enabledTiers.contains(singleTier)
                )
            }
        }
    }

    @Test
    fun `default tier preferences have ADULT_CORE and DOH_ENDPOINTS on, ADULT_EXTENDED and DISTRACTION off`() {
        val defaults = mapOf(
            PolicyTier.ADULT_CORE to true,
            PolicyTier.DOH_ENDPOINTS to true,
            PolicyTier.ADULT_EXTENDED to false,
            PolicyTier.DISTRACTION to false
        )
        for (profile in BlockingProfile.entries) {
            val snapshot = PolicyStoreImpl.deriveSnapshot(profile, defaults)
            assertEquals(setOf(PolicyTier.ADULT_CORE, PolicyTier.DOH_ENDPOINTS), snapshot.enabledTiers)
        }
    }

    @Test
    fun `app blocking is effective only for RELAXED or STRICT with non-empty blocked apps and not paused`() {
        val now = 1_000_000L
        val apps = setOf("com.example.app")

        assertFalse(PolicySnapshot(BlockingProfile.OFF, blockedApps = apps).isAppBlockingEffective(now))
        assertFalse(PolicySnapshot(BlockingProfile.RELAXED, blockedApps = emptySet()).isAppBlockingEffective(now))
        assertFalse(PolicySnapshot(BlockingProfile.STRICT, blockedApps = emptySet()).isAppBlockingEffective(now))

        assertTrue(PolicySnapshot(BlockingProfile.RELAXED, blockedApps = apps).isAppBlockingEffective(now))
        assertTrue(PolicySnapshot(BlockingProfile.STRICT, blockedApps = apps).isAppBlockingEffective(now))
        assertTrue(PolicySnapshot(BlockingProfile.STRICT, privateBlockedApps = apps).isAppBlockingEffective(now))

        // Active pause disables app blocking
        assertFalse(
            PolicySnapshot(BlockingProfile.STRICT, blockedApps = apps, pauseUntil = now + 60_000L)
                .isAppBlockingEffective(now)
        )
        // Expired pause restores app blocking
        assertTrue(
            PolicySnapshot(BlockingProfile.STRICT, blockedApps = apps, pauseUntil = now - 1L)
                .isAppBlockingEffective(now)
        )
    }

    @Test
    fun `dns blocking is effective when tiers or private hashes non-empty and not paused`() {
        val now = 1_000_000L
        assertFalse(PolicySnapshot(enabledTiers = emptySet()).isDnsBlockingEffective(now))
        assertTrue(PolicySnapshot(enabledTiers = setOf(PolicyTier.ADULT_CORE)).isDnsBlockingEffective(now))
        assertTrue(PolicySnapshot(enabledTiers = emptySet(), privateBlockedDomainHashes = setOf(123L)).isDnsBlockingEffective(now))
        assertFalse(
            PolicySnapshot(enabledTiers = setOf(PolicyTier.ADULT_CORE), pauseUntil = now + 5_000L)
                .isDnsBlockingEffective(now)
        )
    }

    @Test
    fun `reserved fields default to null`() {
        val snapshot = PolicySnapshot()
        assertNull(snapshot.reservedTriggerProfileId)
        assertNull(snapshot.reservedRestProfileConfig)
    }
}
