package com.focusforge.app.data.model

/**
 * Blocking profile levels governing enforcer aggressiveness.
 */
enum class BlockingProfile {
    OFF,
    RELAXED,
    STRICT,
    REST // Reserved for Phase 5 & 7 wind-down profile
}

/**
 * Filter categories for content blocking.
 */
enum class DomainCategory {
    ADULT,
    DOH_ENDPOINTS
}

/**
 * Single source of truth immutable snapshot of all active blocking rules.
 * Consumed by AppBlockerService, DnsVpnService, and UI.
 */
data class PolicySnapshot(
    val activeProfile: BlockingProfile = BlockingProfile.OFF,
    val blockedAppPackages: Set<String> = emptySet(),
    val enabledDomainCategories: Set<DomainCategory> = setOf(DomainCategory.ADULT, DomainCategory.DOH_ENDPOINTS),
    val customBlockedDomains: Set<String> = emptySet(),
    val customAllowedDomains: Set<String> = emptySet(),
    val isRestProfileActive: Boolean = false // Reserved for future rest profile
) {
    /**
     * Determines if app blocking should currently be actively enforced.
     * Enforced only under RELAXED, STRICT, or REST, and only if there are blocked apps.
     */
    val isAppBlockingEffective: Boolean
        get() = (activeProfile == BlockingProfile.RELAXED ||
                activeProfile == BlockingProfile.STRICT ||
                activeProfile == BlockingProfile.REST) &&
                blockedAppPackages.isNotEmpty()

    /**
     * Determines if DNS VPN should currently be running.
     * Starts if at least one domain category or custom blocked domain is active.
     */
    val isDnsBlockingEffective: Boolean
        get() = enabledDomainCategories.isNotEmpty() || customBlockedDomains.isNotEmpty()
}
