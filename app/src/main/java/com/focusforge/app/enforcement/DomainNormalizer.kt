package com.focusforge.app.enforcement

import java.net.IDN
import java.util.Locale

/**
 * Canonical domain normalization and 64-bit FNV-1a hashing.
 *
 * Pure JVM unit with zero Android framework dependencies.
 * Normalization rules:
 * 1. Trim whitespace and reject blank/empty strings.
 * 2. Strip optional URL scheme, path, query, fragment, and port if provided.
 * 3. Lowercase using Locale.ROOT.
 * 4. Strip trailing dots (e.g. "example.com." -> "example.com").
 * 5. Convert Internationalized Domain Names (IDN) to ASCII Punycode via RFC 3490 (IDN.toASCII).
 */
object DomainNormalizer {

    const val FNV_OFFSET_BASIS: Long = -0x340d631b7bdddcdbL // 0xcbf29ce484222325UL
    const val FNV_PRIME: Long = 0x100000001b3L

    /**
     * Normalizes a raw domain or URL-like input into a canonical ASCII lowercase domain.
     * Returns empty string if input is blank or cannot be converted to valid Punycode.
     */
    fun normalize(raw: String): String {
        var d = raw.trim()
        if (d.isEmpty()) return ""

        // Strip scheme if present (case-insensitive)
        val lowerStart = d.lowercase(Locale.ROOT)
        if (lowerStart.startsWith("http://")) {
            d = d.substring(7)
        } else if (lowerStart.startsWith("https://")) {
            d = d.substring(8)
        }

        // Strip userinfo, path, query, fragment, and port
        d = d.substringAfter('@')
        d = d.substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .trim()

        if (d.isEmpty()) return ""

        // Strip trailing dots
        while (d.endsWith(".")) {
            d = d.dropLast(1)
        }
        if (d.isEmpty()) return ""

        // Convert Unicode / IDN labels to Punycode ASCII
        val ascii = try {
            IDN.toASCII(d, IDN.ALLOW_UNASSIGNED)
        } catch (_: IllegalArgumentException) {
            return ""
        }

        var normalized = ascii.lowercase(Locale.ROOT)
        while (normalized.endsWith(".")) {
            normalized = normalized.dropLast(1)
        }
        return normalized
    }

    /**
     * Computes 64-bit FNV-1a hash of an already-normalized ASCII domain string.
     */
    fun fnv1a64(normalizedDomain: String): Long {
        var hash = FNV_OFFSET_BASIS
        val bytes = normalizedDomain.toByteArray(Charsets.UTF_8)
        for (b in bytes) {
            hash = (hash xor (b.toLong() and 0xffL)) * FNV_PRIME
        }
        return hash
    }

    /**
     * Normalizes [raw] and returns its 64-bit FNV-1a hash, or null if [raw] normalizes to blank.
     */
    fun normalizeAndHash(raw: String): Long? {
        val normalized = normalize(raw)
        if (normalized.isEmpty()) return null
        return fnv1a64(normalized)
    }
}
