package com.focusforge.app.data.repository

import com.focusforge.app.data.local.entity.DomainBlockCategoryEntity
import com.focusforge.app.data.local.entity.ProfileEntity
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.DomainCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicySnapshotDerivationTest {

    @Test
    fun defaultProfileOff_blocksAdultAndDoh() {
        val categories = listOf(
            DomainBlockCategoryEntity(category = "ADULT", isEnabled = true),
            DomainBlockCategoryEntity(category = "DOH_ENDPOINTS", isEnabled = true)
        )
        val profile = ProfileEntity(id = "default", activeProfile = "OFF")

        val snapshot = PolicyRepositoryImpl.deriveSnapshot(
            profileEntity = profile,
            categories = categories,
            blockedApps = listOf("com.instagram.android"),
            customBlocked = emptyList(),
            customAllowed = emptyList()
        )

        assertEquals(BlockingProfile.OFF, snapshot.activeProfile)
        assertTrue(snapshot.enabledDomainCategories.contains(DomainCategory.ADULT))
        assertTrue(snapshot.enabledDomainCategories.contains(DomainCategory.DOH_ENDPOINTS))
        assertFalse("App blocking must NOT be effective when profile is OFF", snapshot.isAppBlockingEffective)
    }

    @Test
    fun profileRelaxedOrStrict_activatesAppBlocking() {
        val categories = listOf(
            DomainBlockCategoryEntity(category = "ADULT", isEnabled = true),
            DomainBlockCategoryEntity(category = "DOH_ENDPOINTS", isEnabled = true)
        )
        val profile = ProfileEntity(id = "default", activeProfile = "STRICT")

        val snapshot = PolicyRepositoryImpl.deriveSnapshot(
            profileEntity = profile,
            categories = categories,
            blockedApps = listOf("com.instagram.android"),
            customBlocked = emptyList(),
            customAllowed = emptyList()
        )

        assertEquals(BlockingProfile.STRICT, snapshot.activeProfile)
        assertTrue(snapshot.enabledDomainCategories.contains(DomainCategory.ADULT))
        assertTrue("App blocking must be effective under STRICT with blocked apps", snapshot.isAppBlockingEffective)
    }

    @Test
    fun appBlockingInactiveWhenBlockedAppsEmpty() {
        val profile = ProfileEntity(id = "default", activeProfile = "STRICT")

        val snapshot = PolicyRepositoryImpl.deriveSnapshot(
            profileEntity = profile,
            categories = emptyList(),
            blockedApps = emptyList(),
            customBlocked = emptyList(),
            customAllowed = emptyList()
        )

        assertFalse("App blocking must NOT be effective when blocked list is empty", snapshot.isAppBlockingEffective)
    }

    @Test
    fun customDomainsNormalizedLowercaseAndTrimmed() {
        val snapshot = PolicyRepositoryImpl.deriveSnapshot(
            profileEntity = ProfileEntity(id = "default", activeProfile = "OFF"),
            categories = emptyList(),
            blockedApps = emptyList(),
            customBlocked = listOf("  BadSite.COM  ", "DISTRACTION.ORG"),
            customAllowed = listOf(" GoodSite.Org ")
        )

        assertTrue(snapshot.customBlockedDomains.contains("badsite.com"))
        assertTrue(snapshot.customBlockedDomains.contains("distraction.org"))
        assertTrue(snapshot.customAllowedDomains.contains("goodsite.org"))
    }

    @Test
    fun invalidProfileString_fallsBackToOffSafely() {
        val profile = ProfileEntity(id = "default", activeProfile = "CORRUPTED_VALUE")

        val snapshot = PolicyRepositoryImpl.deriveSnapshot(
            profileEntity = profile,
            categories = emptyList(),
            blockedApps = listOf("com.test"),
            customBlocked = emptyList(),
            customAllowed = emptyList()
        )

        assertEquals(BlockingProfile.OFF, snapshot.activeProfile)
        assertFalse(snapshot.isAppBlockingEffective)
    }
}
