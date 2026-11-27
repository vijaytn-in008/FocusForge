package com.focusforge.app.enforcement

import android.content.Context
import android.util.Log
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.PolicyTier
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class BlockDecision {
    ALLOWED,
    BLOCKED
}

enum class DecisionReason {
    SAFETY_ALLOWLIST,
    USER_ALLOWLIST,
    PRIVATE_RULE,
    CATEGORY_ADULT_CORE,
    CATEGORY_ADULT_EXTENDED,
    CATEGORY_DISTRACTION,
    CATEGORY_DOH_ENDPOINTS,
    DEFAULT_ALLOW
}

data class DomainCheckResult(
    val decision: BlockDecision,
    val reason: DecisionReason,
    val matchedTier: PolicyTier? = null
) {
    val isBlocked: Boolean get() = decision == BlockDecision.BLOCKED
}

/**
 * Pure binary-search lookup engine over memory-mapped sorted 64-bit FNV-1a hash buffers.
 * Zero heap HashSets of readable domain strings.
 */
class BlocklistLookup(
    private val tierBuffers: Map<PolicyTier, ByteBuffer>,
    private val publicSuffixBuffer: ByteBuffer? = null
) {

    /**
     * Evaluates [rawDomain] against [snapshot] using the strict decision order:
     * 1. Built-in safety allowlist (non-removable)
     * 2. User allowlist (overrides DISTRACTION & PRIVATE; does not override ADULT unless [allowlistOverridesAdult] is true)
     * 3. Private list (hashed)
     * 4. Category lists per [PolicySnapshot.enabledTiers]
     * 5. Otherwise ALLOW
     */
    fun checkDomain(
        rawDomain: String,
        snapshot: PolicySnapshot,
        configuredUpstreamHashes: Set<Long> = emptySet(),
        allowlistOverridesAdult: Boolean = false
    ): DomainCheckResult {
        val normalized = DomainNormalizer.normalize(rawDomain)
        if (normalized.isEmpty()) {
            return DomainCheckResult(BlockDecision.ALLOWED, DecisionReason.DEFAULT_ALLOW)
        }

        val candidateSuffixes = generateCandidateSuffixes(normalized)
        val candidateHashes = candidateSuffixes.map { DomainNormalizer.fnv1a64(it) }

        // 1. Built-in safety allowlist (always wins)
        if (SafetyAllowlist.isSafetyAllowed(candidateHashes, configuredUpstreamHashes)) {
            return DomainCheckResult(BlockDecision.ALLOWED, DecisionReason.SAFETY_ALLOWLIST)
        }

        // Filter out candidate hashes that are public suffixes / shared hosting parents
        val filterableHashes = if (publicSuffixBuffer != null) {
            candidateHashes.filter { !binarySearchHash(publicSuffixBuffer, it) }
        } else {
            candidateHashes
        }

        if (filterableHashes.isEmpty()) {
            return DomainCheckResult(BlockDecision.ALLOWED, DecisionReason.DEFAULT_ALLOW)
        }

        val isUserAllowed = snapshot.customAllowedDomainHashes.isNotEmpty() &&
            filterableHashes.any { snapshot.customAllowedDomainHashes.contains(it) }

        // If allowlistOverridesAdult is true, user allowlist beats everything below step 1
        if (isUserAllowed && allowlistOverridesAdult) {
            return DomainCheckResult(BlockDecision.ALLOWED, DecisionReason.USER_ALLOWLIST)
        }

        // When allowlistOverridesAdult is false (default per user requirement):
        // Adult tiers remain non-overridable by user allowlist, while DISTRACTION and PRIVATE can be allowed.
        if (isUserAllowed) {
            val adultTier = checkAdultTiers(filterableHashes, snapshot.enabledTiers)
            if (adultTier != null) {
                return DomainCheckResult(
                    decision = BlockDecision.BLOCKED,
                    reason = tierToReason(adultTier),
                    matchedTier = adultTier
                )
            }
            return DomainCheckResult(BlockDecision.ALLOWED, DecisionReason.USER_ALLOWLIST)
        }

        // 3. Private list (hashed)
        if (snapshot.privateBlockedDomainHashes.isNotEmpty() &&
            filterableHashes.any { snapshot.privateBlockedDomainHashes.contains(it) }
        ) {
            return DomainCheckResult(BlockDecision.BLOCKED, DecisionReason.PRIVATE_RULE)
        }

        // 4. Category lists per active profile & enabled tiers
        for (tier in TIER_CHECK_ORDER) {
            if (!snapshot.enabledTiers.contains(tier)) continue
            val buffer = tierBuffers[tier] ?: continue
            if (filterableHashes.any { binarySearchHash(buffer, it) }) {
                return DomainCheckResult(
                    decision = BlockDecision.BLOCKED,
                    reason = tierToReason(tier),
                    matchedTier = tier
                )
            }
        }

        // 5. Otherwise ALLOW
        return DomainCheckResult(BlockDecision.ALLOWED, DecisionReason.DEFAULT_ALLOW)
    }

    private fun checkAdultTiers(
        candidateHashes: List<Long>,
        enabledTiers: Set<PolicyTier>
    ): PolicyTier? {
        for (tier in listOf(PolicyTier.ADULT_CORE, PolicyTier.ADULT_EXTENDED)) {
            if (!enabledTiers.contains(tier)) continue
            val buffer = tierBuffers[tier] ?: continue
            if (candidateHashes.any { binarySearchHash(buffer, it) }) {
                return tier
            }
        }
        return null
    }

    private fun tierToReason(tier: PolicyTier): DecisionReason = when (tier) {
        PolicyTier.ADULT_CORE -> DecisionReason.CATEGORY_ADULT_CORE
        PolicyTier.ADULT_EXTENDED -> DecisionReason.CATEGORY_ADULT_EXTENDED
        PolicyTier.DISTRACTION -> DecisionReason.CATEGORY_DISTRACTION
        PolicyTier.DOH_ENDPOINTS -> DecisionReason.CATEGORY_DOH_ENDPOINTS
    }

    companion object {
        private val TIER_CHECK_ORDER = listOf(
            PolicyTier.ADULT_CORE,
            PolicyTier.ADULT_EXTENDED,
            PolicyTier.DISTRACTION,
            PolicyTier.DOH_ENDPOINTS
        )

        /**
         * Generates suffix candidates from most specific to least specific, stopping before bare TLD.
         * e.g., "a.b.example.com" -> ["a.b.example.com", "b.example.com", "example.com"]
         * "notexample.com" -> ["notexample.com"] (never matches "example.com").
         */
        fun generateCandidateSuffixes(normalizedDomain: String): List<String> {
            val parts = normalizedDomain.split(".")
            if (parts.size <= 1) return emptyList()
            val candidates = ArrayList<String>(parts.size - 1)
            for (i in 0 until parts.size - 1) {
                candidates.add(parts.subList(i, parts.size).joinToString("."))
            }
            return candidates
        }

        /**
         * Unsigned 64-bit binary search over a big-endian ByteBuffer of sorted 8-byte hashes.
         */
        fun binarySearchHash(buffer: ByteBuffer, targetHash: Long): Boolean {
            val numEntries = buffer.capacity() / 8
            if (numEntries <= 0) return false
            var low = 0
            var high = numEntries - 1

            while (low <= high) {
                val mid = (low + high) ushr 1
                val midVal = buffer.getLong(mid * 8)
                val cmp = midVal.toULong().compareTo(targetHash.toULong())
                when {
                    cmp < 0 -> low = mid + 1
                    cmp > 0 -> high = mid - 1
                    else -> return true
                }
            }
            return false
        }
    }
}

/**
 * Android singleton wrapper that copies blocklist binary assets from APK to [Context.filesDir],
 * verifies SHA-256 checksums against `manifest.json`, and memory-maps them read-only.
 */
@Singleton
class BlocklistEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var lookup: BlocklistLookup = BlocklistLookup(emptyMap(), null)
    private var isInitialized = false
    private var lastVerifiedOk = false

    init {
        initialize()
    }

    @Synchronized
    fun initialize() {
        if (isInitialized) return
        try {
            val destDir = File(context.filesDir, "blocklists").apply { mkdirs() }
            val manifestJson = context.assets.open("blocklists/manifest.json").bufferedReader().use { it.readText() }
            val manifest = JSONObject(manifestJson)
            val assetsObj = manifest.getJSONObject("assets")

            val tierFiles = mapOf(
                PolicyTier.ADULT_CORE to "adult_core.bin",
                PolicyTier.ADULT_EXTENDED to "adult_extended.bin",
                PolicyTier.DISTRACTION to "distraction.bin",
                PolicyTier.DOH_ENDPOINTS to "doh_endpoints.bin"
            )

            val tierBuffers = mutableMapOf<PolicyTier, ByteBuffer>()
            var allHashesOk = true

            for ((tier, fileName) in tierFiles) {
                val expectedSha = assetsObj.optJSONObject(fileName)?.optString("sha256", "") ?: ""
                val file = ensureAssetCopiedAndVerified(fileName, expectedSha, destDir)
                if (file != null) {
                    tierBuffers[tier] = mapReadOnly(file)
                } else {
                    allHashesOk = false
                }
            }

            val pslSha = assetsObj.optJSONObject("public_suffix_hashes.bin")?.optString("sha256", "") ?: ""
            val pslFile = ensureAssetCopiedAndVerified("public_suffix_hashes.bin", pslSha, destDir)
            val pslBuffer = pslFile?.let { mapReadOnly(it) }
            if (pslFile == null) allHashesOk = false

            lookup = BlocklistLookup(tierBuffers, pslBuffer)
            lastVerifiedOk = allHashesOk
            isInitialized = true
            Log.i(TAG, "BlocklistEngine initialized (${tierBuffers.size} tiers, hashesOk=$allHashesOk)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BlocklistEngine: ${e.message}", e)
            // Fail open: empty lookup allows all traffic
            lookup = BlocklistLookup(emptyMap(), null)
            lastVerifiedOk = false
        }
    }

    fun areAssetHashesValid(): Boolean = lastVerifiedOk

    fun checkDomain(
        rawDomain: String,
        snapshot: PolicySnapshot,
        configuredUpstreamHashes: Set<Long> = emptySet(),
        allowlistOverridesAdult: Boolean = false
    ): DomainCheckResult {
        return lookup.checkDomain(
            rawDomain = rawDomain,
            snapshot = snapshot,
            configuredUpstreamHashes = configuredUpstreamHashes,
            allowlistOverridesAdult = allowlistOverridesAdult
        )
    }

    private fun ensureAssetCopiedAndVerified(
        fileName: String,
        expectedSha256: String,
        destDir: File
    ): File? {
        val destFile = File(destDir, fileName)
        if (!destFile.exists() || (expectedSha256.isNotEmpty() && sha256(destFile) != expectedSha256)) {
            context.assets.open("blocklists/$fileName").use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        if (expectedSha256.isNotEmpty() && sha256(destFile) != expectedSha256) {
            Log.e(TAG, "SHA-256 verification failed for $fileName")
            return null
        }
        return destFile
    }

    private fun mapReadOnly(file: File): ByteBuffer {
        val raf = RandomAccessFile(file, "r")
        val channel = raf.channel
        val mmap = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        mmap.order(ByteOrder.BIG_ENDIAN)
        return mmap
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            var read = input.read(buf)
            while (read > 0) {
                digest.update(buf, 0, read)
                read = input.read(buf)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "BlocklistEngine"
    }
}
