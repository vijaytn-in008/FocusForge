package com.focusforge.app.enforcement

import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.PolicyTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest

class BlocklistEngineTest {

    private fun resolveAssetsDir(): File {
        val candidates = listOf(
            File("src/main/assets/blocklists"),
            File("app/src/main/assets/blocklists")
        )
        return candidates.firstOrNull { it.exists() && File(it, "manifest.json").exists() }
            ?: error("Could not locate assets/blocklists from ${File(".").absolutePath}")
    }

    private fun mapFile(file: File): ByteBuffer {
        val raf = RandomAccessFile(file, "r")
        val buf = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, raf.channel.size())
        buf.order(ByteOrder.BIG_ENDIAN)
        return buf
    }

    private fun loadFullLookup(): BlocklistLookup {
        val dir = resolveAssetsDir()
        return BlocklistLookup(
            tierBuffers = mapOf(
                PolicyTier.ADULT_CORE to mapFile(File(dir, "adult_core.bin")),
                PolicyTier.ADULT_EXTENDED to mapFile(File(dir, "adult_extended.bin")),
                PolicyTier.DISTRACTION to mapFile(File(dir, "distraction.bin")),
                PolicyTier.DOH_ENDPOINTS to mapFile(File(dir, "doh_endpoints.bin"))
            ),
            publicSuffixBuffer = mapFile(File(dir, "public_suffix_hashes.bin"))
        )
    }

    @Test
    fun `exact and subdomain match while similar name does not match`() {
        val lookup = loadFullLookup()
        val snapshot = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            enabledTiers = setOf(PolicyTier.ADULT_CORE, PolicyTier.DOH_ENDPOINTS)
        )

        val exact = lookup.checkDomain("pornhub.com", snapshot)
        assertTrue(exact.isBlocked)
        assertEquals(DecisionReason.CATEGORY_ADULT_CORE, exact.reason)

        val subdomain = lookup.checkDomain("a.b.pornhub.com.", snapshot)
        assertTrue(subdomain.isBlocked)
        assertEquals(DecisionReason.CATEGORY_ADULT_CORE, subdomain.reason)

        val similar = lookup.checkDomain("notpornhub.com", snapshot)
        assertFalse("notpornhub.com must NOT match pornhub.com", similar.isBlocked)
        assertEquals(DecisionReason.DEFAULT_ALLOW, similar.reason)
    }

    @Test
    fun `user allowlist overrides distraction and private rules but not adult by default`() {
        val lookup = loadFullLookup()
        val youtubeHash = DomainNormalizer.normalizeAndHash("youtube.com")!!
        val pornhubHash = DomainNormalizer.normalizeAndHash("pornhub.com")!!
        val privateHash = DomainNormalizer.normalizeAndHash("custom-distract.example")!!

        val snapshot = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            enabledTiers = setOf(PolicyTier.ADULT_CORE, PolicyTier.DISTRACTION),
            customAllowedDomainHashes = setOf(youtubeHash, pornhubHash, privateHash),
            privateBlockedDomainHashes = setOf(privateHash)
        )

        // Distraction allowed by user allowlist
        val ytRes = lookup.checkDomain("m.youtube.com", snapshot)
        assertFalse(ytRes.isBlocked)
        assertEquals(DecisionReason.USER_ALLOWLIST, ytRes.reason)

        // Private rule overridden by user allowlist
        val privRes = lookup.checkDomain("custom-distract.example", snapshot)
        assertFalse(privRes.isBlocked)
        assertEquals(DecisionReason.USER_ALLOWLIST, privRes.reason)

        // Adult remains blocked when allowlistOverridesAdult=false (default)
        val adultRes = lookup.checkDomain("pornhub.com", snapshot, allowlistOverridesAdult = false)
        assertTrue(adultRes.isBlocked)
        assertEquals(DecisionReason.CATEGORY_ADULT_CORE, adultRes.reason)
    }

    @Test
    fun `safety allowlist overrides all categories and private rules`() {
        val lookup = loadFullLookup()
        val connectivityHash = DomainNormalizer.normalizeAndHash("connectivitycheck.gstatic.com")!!
        val configuredUpstream = DomainNormalizer.normalizeAndHash("dns.google")!!

        val snapshot = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            enabledTiers = PolicyTier.entries.toSet(),
            privateBlockedDomainHashes = setOf(connectivityHash)
        )

        val connectivity = lookup.checkDomain("connectivitycheck.gstatic.com", snapshot)
        assertFalse(connectivity.isBlocked)
        assertEquals(DecisionReason.SAFETY_ALLOWLIST, connectivity.reason)

        // Configured upstream DoH bootstrap hostname is safety-allowed even though DOH_ENDPOINTS is enabled
        val upstream = lookup.checkDomain(
            "dns.google",
            snapshot,
            configuredUpstreamHashes = setOf(configuredUpstream)
        )
        assertFalse(upstream.isBlocked)
        assertEquals(DecisionReason.SAFETY_ALLOWLIST, upstream.reason)

        // Unconfigured DoH endpoint is still blocked
        val otherDoh = lookup.checkDomain(
            "cloudflare-dns.com",
            snapshot,
            configuredUpstreamHashes = setOf(configuredUpstream)
        )
        assertTrue(otherDoh.isBlocked)
        assertEquals(DecisionReason.CATEGORY_DOH_ENDPOINTS, otherDoh.reason)
    }

    @Test
    fun `public suffix and shared hosting parents are never blocked even if in private list`() {
        val lookup = loadFullLookup()
        val comHash = DomainNormalizer.fnv1a64("com")
        val githubIoHash = DomainNormalizer.fnv1a64("github.io")
        val coUkHash = DomainNormalizer.fnv1a64("co.uk")

        val snapshot = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            enabledTiers = PolicyTier.entries.toSet(),
            privateBlockedDomainHashes = setOf(comHash, githubIoHash, coUkHash)
        )

        assertFalse(lookup.checkDomain("github.io", snapshot).isBlocked)
        assertFalse(lookup.checkDomain("user.github.io", snapshot).isBlocked)
        assertFalse(lookup.checkDomain("example.co.uk", snapshot).isBlocked)
    }

    @Test
    fun `asset sha256 checksums match manifest json`() {
        val dir = resolveAssetsDir()
        val manifestText = File(dir, "manifest.json").readText()
        val entryRegex = Regex(""""([^"]+\.bin)"\s*:\s*\{[^}]*"sha256"\s*:\s*"([0-9a-f]{64})"""")
        val matches = entryRegex.findAll(manifestText).toList()
        assertEquals("Expected 5 binary assets in manifest.json", 5, matches.size)
        for (m in matches) {
            val name = m.groupValues[1]
            val expectedSha = m.groupValues[2]
            val file = File(dir, name)
            assertTrue("Missing asset $name", file.exists())
            val digest = MessageDigest.getInstance("SHA-256")
            val actualSha = digest.digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("SHA-256 mismatch for $name", expectedSha, actualSha)
        }
    }

    @Test
    fun `performance on full adult_core list is sub-millisecond per lookup`() {
        val lookup = loadFullLookup()
        val snapshot = PolicySnapshot(
            activeProfile = BlockingProfile.STRICT,
            enabledTiers = PolicyTier.entries.toSet()
        )
        val domains = listOf(
            "google.com", "gemini.google.com", "wikipedia.org",
            "pornhub.com", "a.b.xvideos.com", "github.com",
            "tiktok.com", "cloudflare-dns.com", "example.org", "notpornhub.com"
        )

        // Warmup
        for (d in domains) lookup.checkDomain(d, snapshot)

        val startNs = System.nanoTime()
        repeat(100) {
            for (d in domains) {
                lookup.checkDomain(d, snapshot)
            }
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
        // 1000 full multi-tier + PSL binary searches should take well under 150ms
        assertTrue("1000 lookups took ${elapsedMs}ms (expected < 150ms)", elapsedMs < 150.0)
    }
}
