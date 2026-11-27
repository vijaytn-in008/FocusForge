package com.focusforge.app.data.model

/**
 * Blocking profile levels governing enforcer behavior.
 *
 * Profile-to-Category Table:
 * | Profile | App Blocking      | ADULT_CORE | ADULT_EXTENDED | DISTRACTION | DOH_ENDPOINTS |
 * |---------|-------------------|------------|----------------|-------------|---------------|
 * | OFF     | Inactive          | User pref  | User pref      | Inactive    | User pref     |
 * | RELAXED | Active (non-empty)| User pref  | User pref      | Inactive    | User pref     |
 * | STRICT  | Active (non-empty)| User pref  | User pref      | User pref   | User pref     |
 *
 * Notes:
 * - ADULT_CORE and DOH_ENDPOINTS default to ON as baseline infrastructure.
 * - ADULT_EXTENDED defaults to OFF (aggressive list, opt-in with warning).
 * - DISTRACTION is never a domain-wide default; it applies only under profiles that choose it (STRICT).
 */
enum class BlockingProfile {
    OFF,
    RELAXED,
    STRICT
}

/**
 * High-level blocklist categories.
 */
enum class BlockCategory {
    ADULT,
    DISTRACTION,
    DOH_ENDPOINTS
}

/**
 * Tier within a category.
 */
enum class BlockTier {
    CORE,
    EXTENDED
}

/**
 * Specific category + tier combinations supported by the blocklist engine.
 */
enum class PolicyTier(val category: BlockCategory, val tier: BlockTier) {
    ADULT_CORE(BlockCategory.ADULT, BlockTier.CORE),
    ADULT_EXTENDED(BlockCategory.ADULT, BlockTier.EXTENDED),
    DISTRACTION(BlockCategory.DISTRACTION, BlockTier.CORE),
    DOH_ENDPOINTS(BlockCategory.DOH_ENDPOINTS, BlockTier.CORE)
}

/**
 * Explicit upstream DNS resolver selection (default = active network's DNS resolvers).
 */
enum class UpstreamResolverProvider(
    val displayName: String,
    val addresses: List<String>,
    val bootstrapHostnames: List<String>
) {
    SYSTEM_DEFAULT("System Default (Active Network)", emptyList(), emptyList()),
    CLOUDFLARE("Cloudflare (1.1.1.1)", listOf("1.1.1.1", "2606:4700:4700::1111"), listOf("one.one.one.one", "cloudflare-dns.com")),
    GOOGLE("Google (8.8.8.8)", listOf("8.8.8.8", "2001:4860:4860::8888"), listOf("dns.google", "dns.google.com")),
    QUAD9("Quad9 (9.9.9.9)", listOf("9.9.9.9", "2620:fe::fe"), listOf("dns.quad9.net"))
}

/**
 * Response type for blocked DNS queries.
 */
enum class BlockResponseMode(val displayName: String) {
    NXDOMAIN("NXDOMAIN (Domain does not exist)"),
    ZERO_IP("0.0.0.0 / :: (Null route)")
}

/**
 * Immutable snapshot of all active enforcement rules, exposed by PolicyStore as StateFlow.
 * Enforcement services read ONLY this snapshot and never touch Room or SharedPreferences directly.
 */
data class PolicySnapshot(
    val activeProfile: BlockingProfile = BlockingProfile.OFF,
    val blockedApps: Set<String> = emptySet(),
    val enabledTiers: Set<PolicyTier> = setOf(PolicyTier.ADULT_CORE, PolicyTier.DOH_ENDPOINTS),
    val customAllowedDomainHashes: Set<Long> = emptySet(),
    val privateBlockedDomainHashes: Set<Long> = emptySet(),
    val privateBlockedApps: Set<String> = emptySet(),
    val pauseUntil: Long? = null,
    // Reserved nullable fields for Phase 4 trigger profiles and REST profile (not implemented in Phase 3)
    val reservedTriggerProfileId: String? = null,
    val reservedRestProfileConfig: String? = null
) {
    /**
     * Returns true if protection is currently temporarily paused.
     */
    fun isPaused(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val until = pauseUntil ?: return false
        return nowMillis < until
    }

    /**
     * App watcher polls ONLY while (screen on) AND (a profile with app blocking is active)
     * AND (blocked app set non-empty) AND (not paused).
     */
    fun isAppBlockingEffective(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isPaused(nowMillis)) return false
        val profileActive = activeProfile == BlockingProfile.RELAXED || activeProfile == BlockingProfile.STRICT
        return profileActive && (blockedApps.isNotEmpty() || privateBlockedApps.isNotEmpty())
    }

    /**
     * DNS VPN runs only when something needs DNS filtering and protection is not paused.
     */
    fun isDnsBlockingEffective(nowMillis: Long = System.currentTimeMillis()): Boolean {
        if (isPaused(nowMillis)) return false
        return enabledTiers.isNotEmpty() || privateBlockedDomainHashes.isNotEmpty()
    }
}
