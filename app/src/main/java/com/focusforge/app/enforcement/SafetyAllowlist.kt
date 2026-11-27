package com.focusforge.app.enforcement

/**
 * Built-in, non-removable safety allowlist (Decision Order Step 1).
 *
 * Every entry is documented below and hashed at initialization time using [DomainNormalizer.fnv1a64].
 * Covers:
 * 1. Android connectivity-check & captive-portal domains
 * 2. OS, Google Play, push messaging (FCM), & time synchronization services
 * 3. Emergency, public health, & government services
 * 4. Dynamically configured upstream resolver / DoH bootstrap hostnames (passed per-query/config)
 */
object SafetyAllowlist {

    /**
     * Documented safety domains (lowercase ASCII).
     */
    val DOCUMENTED_ENTRIES: List<String> = listOf(
        // 1. Android & OS connectivity-check / captive-portal domains
        "connectivitycheck.gstatic.com",
        "connectivitycheck.android.com",
        "clients3.google.com",
        "clients4.google.com",
        "captive.apple.com",
        "detectportal.firefox.com",
        "msftconnecttest.com",
        "msftncsi.com",
        "nmcheck.gnome.org",

        // 2. OS, Google Play, push notifications (FCM), & NTP time services
        "play.google.com",
        "play.googleapis.com",
        "android.clients.google.com",
        "fcm.googleapis.com",
        "mtalk.google.com",
        "ota.googlezip.net",
        "dl.google.com",
        "gvt1.com",
        "gvt2.com",
        "time.android.com",
        "time.google.com",
        "pool.ntp.org",

        // 3. Emergency, government, & public health services
        "911.gov",
        "112.eu",
        "ready.gov",
        "fema.gov",
        "who.int",
        "cdc.gov",
        "nih.gov",
        "sos.gov.in",
        "uidai.gov.in",
        "emergency.gov.au"
    )

    private val staticHashes: Set<Long> = DOCUMENTED_ENTRIES
        .mapNotNull { DomainNormalizer.normalizeAndHash(it) }
        .toSet()

    /**
     * Returns true if any suffix candidate hash in [candidateHashes] belongs to the
     * built-in safety allowlist or [configuredUpstreamHashes].
     */
    fun isSafetyAllowed(
        candidateHashes: List<Long>,
        configuredUpstreamHashes: Set<Long> = emptySet()
    ): Boolean {
        for (h in candidateHashes) {
            if (staticHashes.contains(h)) return true
            if (configuredUpstreamHashes.isNotEmpty() && configuredUpstreamHashes.contains(h)) return true
        }
        return false
    }
}
