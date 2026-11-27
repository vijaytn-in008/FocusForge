package com.focusforge.app.data.repository

import android.content.Context
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.PolicyTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for enforcement policy.
 * Exposes an immutable [PolicySnapshot] as [StateFlow].
 */
interface PolicyStore {
    val policySnapshot: StateFlow<PolicySnapshot>
    val tierPreferences: StateFlow<Map<PolicyTier, Boolean>>

    suspend fun setActiveProfile(profile: BlockingProfile)
    suspend fun setTierEnabled(tier: PolicyTier, enabled: Boolean)
    suspend fun setPauseUntil(pauseUntilMillis: Long?)
}

@Singleton
class PolicyStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
    blockedAppDao: BlockedAppDao
) : PolicyStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeProfile = MutableStateFlow(loadProfile())
    private val _tierPreferences = MutableStateFlow(loadTierPreferences())
    private val _pauseUntil = MutableStateFlow<Long?>(loadPauseUntil())

    override val tierPreferences: StateFlow<Map<PolicyTier, Boolean>> = _tierPreferences.asStateFlow()

    override val policySnapshot: StateFlow<PolicySnapshot> = combine(
        _activeProfile,
        _tierPreferences,
        blockedAppDao.getBlockedPackageNames(),
        _pauseUntil
    ) { profile, tierPrefs, blockedApps, pauseUntil ->
        deriveSnapshot(
            activeProfile = profile,
            tierPreferences = tierPrefs,
            blockedApps = blockedApps.toSet(),
            pauseUntil = pauseUntil
        )
    }.stateIn(
        scope = storeScope,
        started = SharingStarted.Eagerly,
        initialValue = deriveSnapshot(
            activeProfile = _activeProfile.value,
            tierPreferences = _tierPreferences.value,
            blockedApps = emptySet(),
            pauseUntil = _pauseUntil.value
        )
    )

    override suspend fun setActiveProfile(profile: BlockingProfile) {
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profile.name).apply()
        _activeProfile.value = profile
    }

    override suspend fun setTierEnabled(tier: PolicyTier, enabled: Boolean) {
        prefs.edit().putBoolean(tierPrefKey(tier), enabled).apply()
        val updated = _tierPreferences.value.toMutableMap()
        updated[tier] = enabled
        _tierPreferences.value = updated
    }

    override suspend fun setPauseUntil(pauseUntilMillis: Long?) {
        if (pauseUntilMillis == null) {
            prefs.edit().remove(KEY_PAUSE_UNTIL).apply()
        } else {
            prefs.edit().putLong(KEY_PAUSE_UNTIL, pauseUntilMillis).apply()
        }
        _pauseUntil.value = pauseUntilMillis
    }

    private fun loadProfile(): BlockingProfile {
        val raw = prefs.getString(KEY_ACTIVE_PROFILE, BlockingProfile.OFF.name)
        return try {
            BlockingProfile.valueOf(raw ?: BlockingProfile.OFF.name)
        } catch (_: Exception) {
            BlockingProfile.OFF
        }
    }

    private fun loadTierPreferences(): Map<PolicyTier, Boolean> {
        return PolicyTier.entries.associateWith { tier ->
            val defaultEnabled = when (tier) {
                PolicyTier.ADULT_CORE -> true
                PolicyTier.DOH_ENDPOINTS -> true
                PolicyTier.ADULT_EXTENDED -> false
                PolicyTier.DISTRACTION -> false
            }
            prefs.getBoolean(tierPrefKey(tier), defaultEnabled)
        }
    }

    private fun loadPauseUntil(): Long? {
        if (!prefs.contains(KEY_PAUSE_UNTIL)) return null
        val value = prefs.getLong(KEY_PAUSE_UNTIL, 0L)
        return if (value > 0L) value else null
    }

    private fun tierPrefKey(tier: PolicyTier): String = "tier_${tier.name.lowercase()}"

    companion object {
        private const val PREFS_NAME = "focusforge_policy_prefs"
        private const val KEY_ACTIVE_PROFILE = "active_profile"
        private const val KEY_PAUSE_UNTIL = "pause_until"

        /**
         * Pure snapshot derivation function implementing the Profile-to-Category Table.
         *
         * Profile-to-Category rules:
         * - ADULT_CORE: enabled if user preference is true (default true).
         * - ADULT_EXTENDED: enabled if user preference is true (default false).
         * - DOH_ENDPOINTS: enabled if user preference is true (default true).
         * - DISTRACTION: enabled ONLY when [activeProfile] is [BlockingProfile.STRICT]
         *   AND user preference for DISTRACTION is true (default false).
         *   Never enabled as a domain-wide default under OFF or RELAXED.
         */
        fun deriveSnapshot(
            activeProfile: BlockingProfile,
            tierPreferences: Map<PolicyTier, Boolean>,
            blockedApps: Set<String> = emptySet(),
            customAllowedDomainHashes: Set<Long> = emptySet(),
            privateBlockedDomainHashes: Set<Long> = emptySet(),
            privateBlockedApps: Set<String> = emptySet(),
            pauseUntil: Long? = null
        ): PolicySnapshot {
            val effectiveTiers = mutableSetOf<PolicyTier>()

            if (tierPreferences[PolicyTier.ADULT_CORE] != false) {
                effectiveTiers.add(PolicyTier.ADULT_CORE)
            }
            if (tierPreferences[PolicyTier.ADULT_EXTENDED] == true) {
                effectiveTiers.add(PolicyTier.ADULT_EXTENDED)
            }
            if (tierPreferences[PolicyTier.DOH_ENDPOINTS] != false) {
                effectiveTiers.add(PolicyTier.DOH_ENDPOINTS)
            }
            if (activeProfile == BlockingProfile.STRICT && tierPreferences[PolicyTier.DISTRACTION] == true) {
                effectiveTiers.add(PolicyTier.DISTRACTION)
            }

            return PolicySnapshot(
                activeProfile = activeProfile,
                blockedApps = blockedApps,
                enabledTiers = effectiveTiers,
                customAllowedDomainHashes = customAllowedDomainHashes,
                privateBlockedDomainHashes = privateBlockedDomainHashes,
                privateBlockedApps = privateBlockedApps,
                pauseUntil = pauseUntil
            )
        }
    }
}
