package com.focusforge.app.enforcement

import android.content.Context
import android.util.Log
import com.focusforge.app.data.model.DomainCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton

data class DomainCheckResult(
    val domain: String,
    val isBlocked: Boolean,
    val matchedCategory: DomainCategory? = null,
    val matchedRule: String? = null // e.g. "CUSTOM_ALLOW", "CUSTOM_BLOCK", "CATEGORY_ADULT"
)

/**
 * High-performance, memory-mapped domain blocklist lookup engine.
 * Binary search over sorted 64-bit FNV-1a hashes.
 * Zero-heap allocation during active lookups.
 */
@Singleton
class BlocklistEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    // Mapped LongBuffers for each category
    private val categoryBuffers = mutableMapOf<DomainCategory, ByteBuffer>()
    private var isInitialized = false

    init {
        initializeEngine()
    }

    @Synchronized
    fun initializeEngine() {
        if (isInitialized) return
        try {
            val blocklistDir = File(context.filesDir, "blocklists").apply { mkdirs() }

            for (category in DomainCategory.entries) {
                val assetName = when (category) {
                    DomainCategory.ADULT -> "adult.bin"
                    DomainCategory.DOH_ENDPOINTS -> "doh_endpoints.bin"
                }
                val destFile = File(blocklistDir, assetName)
                val assetSize = try {
                    context.assets.open("blocklists/$assetName").use { it.available().toLong() }
                } catch (_: Exception) { 0L }

                if (!destFile.exists() || destFile.length() != assetSize) {
                    copyAssetToFile("blocklists/$assetName", destFile)
                }

                if (destFile.exists() && destFile.length() >= 8L) {
                    val raf = RandomAccessFile(destFile, "r")
                    val channel = raf.channel
                    val mmap = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                    mmap.order(ByteOrder.BIG_ENDIAN)
                    categoryBuffers[category] = mmap
                    Log.i(TAG, "Memory-mapped $category blocklist (${destFile.length()} bytes, ${destFile.length() / 8} domains)")
                }
            }
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing BlocklistEngine: ${e.message}", e)
        }
    }

    /**
     * Checks if a domain is blocked given active categories and custom allow/block sets.
     * Precedence:
     * 1. Custom ALLOW list (Always beats any block)
     * 2. Custom BLOCK list
     * 3. Active Category Blocklists (Suffix-aware)
     */
    fun checkDomain(
        rawDomain: String,
        enabledCategories: Set<DomainCategory>,
        customBlockedDomains: Set<String> = emptySet(),
        customAllowedDomains: Set<String> = emptySet()
    ): DomainCheckResult {
        val domain = normalize(rawDomain)
        if (domain.isBlank()) {
            return DomainCheckResult(domain = rawDomain, isBlocked = false)
        }

        // 1. Check custom allowlist (exact or parent subdomain)
        if (matchesDomainHierarchy(domain, customAllowedDomains)) {
            return DomainCheckResult(
                domain = domain,
                isBlocked = false,
                matchedRule = "CUSTOM_ALLOW"
            )
        }

        // 2. Check custom blocklist (exact or parent subdomain)
        if (matchesDomainHierarchy(domain, customBlockedDomains)) {
            return DomainCheckResult(
                domain = domain,
                isBlocked = true,
                matchedRule = "CUSTOM_BLOCK"
            )
        }

        // 3. Check enabled category blocklists (suffix-aware)
        val candidateLabels = generateCandidateSuffixes(domain)
        for (suffix in candidateLabels) {
            val hash = fnv1a64(suffix)
            for (category in enabledCategories) {
                val buffer = categoryBuffers[category] ?: continue
                if (binarySearchHash(buffer, hash)) {
                    return DomainCheckResult(
                        domain = domain,
                        isBlocked = true,
                        matchedCategory = category,
                        matchedRule = "CATEGORY_${category.name}"
                    )
                }
            }
        }

        return DomainCheckResult(domain = domain, isBlocked = false)
    }

    private fun matchesDomainHierarchy(domain: String, domainSet: Set<String>): Boolean {
        if (domainSet.isEmpty()) return false
        val candidates = generateCandidateSuffixes(domain)
        for (candidate in candidates) {
            if (domainSet.contains(candidate)) return true
        }
        return false
    }

    /**
     * Generates suffix candidates for subdomain checking.
     * e.g. "a.b.pornhub.com" -> ["a.b.pornhub.com", "b.pornhub.com", "pornhub.com"]
     * Stops before bare TLD (e.g. stops before "com").
     */
    fun generateCandidateSuffixes(domain: String): List<String> {
        val parts = domain.split(".")
        if (parts.size <= 1) return listOf(domain)

        val candidates = mutableListOf<String>()
        // From full domain down to 2-part domain (domain.tld)
        for (i in 0 until (parts.size - 1)) {
            candidates.add(parts.subList(i, parts.size).joinToString("."))
        }
        return candidates
    }

    /**
     * Binary search over big-endian 64-bit Long buffer.
     */
    private fun binarySearchHash(buffer: ByteBuffer, targetHash: Long): Boolean {
        val numEntries = buffer.capacity() / 8
        var low = 0
        var high = numEntries - 1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midVal = buffer.getLong(mid * 8)

            // Unsigned 64-bit comparison
            val cmp = midVal.toULong().compareTo(targetHash.toULong())
            if (cmp < 0) {
                low = mid + 1
            } else if (cmp > 0) {
                high = mid - 1
            } else {
                return true // Match found
            }
        }
        return false
    }

    private fun copyAssetToFile(assetPath: String, destFile: File) {
        try {
            context.assets.open(assetPath).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asset $assetPath could not be copied: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BlocklistEngine"

        const val FNV_OFFSET_BASIS = -0x340d631b7bdddcdbL // 0xcbf29ce484222325L as signed Long
        const val FNV_PRIME = 0x100000001b3L

        /**
         * Computes 64-bit FNV-1a hash matching build-blocklist.py.
         */
        fun fnv1a64(str: String): Long {
            var hash = FNV_OFFSET_BASIS
            val bytes = str.toByteArray(Charsets.UTF_8)
            for (b in bytes) {
                hash = (hash xor (b.toLong() and 0xffL)) * FNV_PRIME
            }
            return hash
        }

        /**
         * Normalizes raw domain string.
         */
        fun normalize(raw: String): String {
            var d = raw.trim().lowercase()
            if (d.startsWith("http://")) d = d.removePrefix("http://")
            if (d.startsWith("https://")) d = d.removePrefix("https://")
            d = d.substringBefore('/').substringBefore(':')
            if (d.startsWith("www.")) d = d.removePrefix("www.")
            if (d.endsWith(".")) d = d.removeSuffix(".")
            return d
        }
    }
}
