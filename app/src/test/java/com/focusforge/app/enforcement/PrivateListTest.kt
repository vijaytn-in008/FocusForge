package com.focusforge.app.enforcement

import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.PolicySnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PrivateListTest {

    @Test
    fun `maskDomain and maskLabel hide sensitive names`() {
        assertEquals("p•••••.com", PrivateMasker.maskDomain("pornhub.com"))
        assertEquals("s•••••.example.org", PrivateMasker.maskDomain("sub.example.org"))
        assertEquals("a•.com", PrivateMasker.maskDomain("ab.com"))
        assertEquals("R•••••", PrivateMasker.maskLabel("Reddit"))
        assertEquals("•••••", PrivateMasker.maskDomain("   "))
    }

    @Test
    fun `private domain hashes match exact and subdomain but not similar names`() {
        val secretHash = DomainNormalizer.normalizeAndHash("secret-site.example")!!
        val snapshot = PolicySnapshot(
            activeProfile = BlockingProfile.OFF,
            enabledTiers = emptySet(),
            privateBlockedDomainHashes = setOf(secretHash)
        )
        val lookup = BlocklistLookup(emptyMap())

        val exact = lookup.checkDomain("secret-site.example", snapshot)
        assertTrue(exact.isBlocked)
        assertEquals(DecisionReason.PRIVATE_RULE, exact.reason)

        val sub = lookup.checkDomain("a.b.secret-site.example.", snapshot)
        assertTrue(sub.isBlocked)
        assertEquals(DecisionReason.PRIVATE_RULE, sub.reason)

        val similar = lookup.checkDomain("notsecret-site.example", snapshot)
        assertFalse(similar.isBlocked)
        assertEquals(DecisionReason.DEFAULT_ALLOW, similar.reason)
    }

    @Test
    fun `backup exclusion rules exclude private hashes and vault`() {
        val candidates = listOf(File("src/main/res/xml"), File("app/src/main/res/xml"))
        val xmlDir = candidates.firstOrNull { it.exists() } ?: error("Missing res/xml")

        val dataExtraction = File(xmlDir, "data_extraction_rules.xml").readText()
        assertTrue(dataExtraction.contains("focusforge_private_hashes.xml"))
        assertTrue(dataExtraction.contains("private_vault"))

        val fullBackup = File(xmlDir, "full_backup_content.xml").readText()
        assertTrue(fullBackup.contains("focusforge_private_hashes.xml"))
        assertTrue(fullBackup.contains("private_vault"))
    }
}
