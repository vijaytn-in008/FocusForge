package com.focusforge.app.data.repository

import android.content.Context
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.model.BlockResponseMode
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.PolicySnapshot
import com.focusforge.app.data.model.PolicyTier
import com.focusforge.app.data.model.UpstreamResolverProvider
import com.focusforge.app.enforcement.DomainNormalizer
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
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for enforcement policy.
 * Exposes an immutable [PolicySnapshot] as [StateFlow].
 * Zero readable domain names are ever persisted to disk or logs.
 */
interface PolicyStore {
    val policySnapshot: StateFlow<PolicySnapshot>
    val tierPreferences: StateFlow<Map<PolicyTier, Boolean>>
    val upstreamProvider: StateFlow<UpstreamResolverProvider>
    val blockResponseMode: StateFlow<BlockResponseMode>
    val excludeOwnAppFromVpn: StateFlow<Boolean>
    val todayBlockedCount: StateFlow<Int>
    val pauseEventsCount: StateFlow<Int>
    val isDnsProtectionEnabled: StateFlow<Boolean>

    suspend fun setActiveProfile(profile: BlockingProfile)
    suspend fun setTierEnabled(tier: PolicyTier, enabled: Boolean)
    suspend fun setPauseUntil(pauseUntilMillis: Long?)
    suspend fun setUpstreamProvider(provider: UpstreamResolverProvider)
    suspend fun setBlockResponseMode(mode: BlockResponseMode)
    suspend fun setExcludeOwnAppFromVpn(exclude: Boolean)
    suspend fun setDnsProtectionEnabled(enabled: Boolean)

    suspend fun addCustomAllowDomain(rawDomain: String): Boolean
    suspend fun removeCustomAllowDomain(rawDomain: String): Boolean
    suspend fun clearCustomAllowDomains()

    suspend fun incrementTodayBlockedCount()
    suspend fun recordPauseEvent()
}

@Singleton
class PolicyStoreImpl @Inject constructor(
    @ApplicationContext context: Context,
    blockedAppDao: BlockedAppDao,
    privateListStore: PrivateListStore
) : PolicyStore {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _activeProfile = MutableStateFlow(loadProfile())
    private val _tierPreferences = MutableStateFlow(loadTierPreferences())
    private val _customAllowedHashes = MutableStateFlow(loadAllowedHashes())
    private val _pauseUntil = MutableStateFlow<Long?>(loadPauseUntil())
    private val _upstreamProvider = MutableStateFlow(loadUpstreamProvider())
    private val _blockResponseMode = MutableStateFlow(loadBlockResponseMode())
    private val _excludeOwnAppFromVpn = MutableStateFlow(prefs.getBoolean(KEY_EXCLUDE_OWN_APP, true))
    private val _todayBlockedCount = MutableStateFlow(loadTodayBlockedCount())
    private val _pauseEventsCount = MutableStateFlow(prefs.getInt(KEY_PAUSE_EVENTS_COUNT, 0))
    private val _isDnsProtectionEnabled = MutableStateFlow(prefs.getBoolean(KEY_DNS_PROTECTION_ENABLED, false))

    override val tierPreferences: StateFlow<Map<PolicyTier, Boolean>> = _tierPreferences.asStateFlow()
    override val upstreamProvider: StateFlow<UpstreamResolverProvider> = _upstreamProvider.asStateFlow()
    override val blockResponseMode: StateFlow<BlockResponseMode> = _blockResponseMode.asStateFlow()
    override val excludeOwnAppFromVpn: StateFlow<Boolean> = _excludeOwnAppFromVpn.asStateFlow()
    override val todayBlockedCount: StateFlow<Int> = _todayBlockedCount.asStateFlow()
    override val pauseEventsCount: StateFlow<Int> = _pauseEventsCount.asStateFlow()
    override val isDnsProtectionEnabled: StateFlow<Boolean> = _isDnsProtectionEnabled.asStateFlow()

    override val policySnapshot: StateFlow<PolicySnapshot> = combine(
        combine(
            _activeProfile,
            _tierPreferences,
            blockedAppDao.getBlockedPackageNames(),
            _customAllowedHashes,
            _pauseUntil
        ) { profile, tierPrefs, blockedApps, allowedHashes, pauseUntil ->
            BasePolicyBundle(profile, tierPrefs, blockedApps.toSet(), allowedHashes, pauseUntil)
        },
        privateListStore.privateDomainHashes,
        privateListStore.privateBlockedApps
    ) { base, privateDomainHashes, privateApps ->
        deriveSnapshot(
            activeProfile = base.profile,
            tierPreferences = base.tierPrefs,
            blockedApps = base.blockedApps,
            customAllowedDomainHashes = base.allowedHashes,
            privateBlockedDomainHashes = privateDomainHashes,
            privateBlockedApps = privateApps,
            pauseUntil = base.pauseUntil
        )
    }.stateIn(
        scope = storeScope,
        started = SharingStarted.Eagerly,
        initialValue = deriveSnapshot(
            activeProfile = _activeProfile.value,
            tierPreferences = _tierPreferences.value,
            blockedApps = emptySet(),
            customAllowedDomainHashes = _customAllowedHashes.value,
            privateBlockedDomainHashes = privateListStore.privateDomainHashes.value,
            privateBlockedApps = privateListStore.privateBlockedApps.value,
            pauseUntil = _pauseUntil.value
        )
    )

    private data class BasePolicyBundle(
        val profile: BlockingProfile,
        val tierPrefs: Map<PolicyTier, Boolean>,
        val blockedApps: Set<String>,
        val allowedHashes: Set<Long>,
        val pauseUntil: Long?
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

    override suspend fun setUpstreamProvider(provider: UpstreamResolverProvider) {
        prefs.edit().putString(KEY_UPSTREAM_PROVIDER, provider.name).apply()
        _upstreamProvider.value = provider
    }

    override suspend fun setBlockResponseMode(mode: BlockResponseMode) {
        prefs.edit().putString(KEY_BLOCK_RESPONSE_MODE, mode.name).apply()
        _blockResponseMode.value = mode
    }

    override suspend fun setExcludeOwnAppFromVpn(exclude: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDE_OWN_APP, exclude).apply()
        _excludeOwnAppFromVpn.value = exclude
    }

    override suspend fun setDnsProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DNS_PROTECTION_ENABLED, enabled).apply()
        _isDnsProtectionEnabled.value = enabled
    }

    override suspend fun addCustomAllowDomain(rawDomain: String): Boolean {
        val hash = DomainNormalizer.normalizeAndHash(rawDomain) ?: return false
        val updated = _customAllowedHashes.value + hash
        saveAllowedHashes(updated)
        _customAllowedHashes.value = updated
        return true
    }

    override suspend fun removeCustomAllowDomain(rawDomain: String): Boolean {
        val hash = DomainNormalizer.normalizeAndHash(rawDomain) ?: return false
        if (!_customAllowedHashes.value.contains(hash)) return false
        val updated = _customAllowedHashes.value - hash
        saveAllowedHashes(updated)
        _customAllowedHashes.value = updated
        return true
    }

    override suspend fun clearCustomAllowDomains() {
        saveAllowedHashes(emptySet())
        _customAllowedHashes.value = emptySet()
    }

    private val counterLock = Any()

    override suspend fun incrementTodayBlockedCount() {
        synchronized(counterLock) {
            val todayKey = currentDayKey()
            val storedDay = prefs.getInt(KEY_BLOCKED_DAY, -1)
            val current = if (storedDay == todayKey) prefs.getInt(KEY_BLOCKED_COUNT, 0) else 0
            val next = current + 1
            prefs.edit()
                .putInt(KEY_BLOCKED_DAY, todayKey)
                .putInt(KEY_BLOCKED_COUNT, next)
                .apply()
            _todayBlockedCount.value = next
        }
    }

    override suspend fun recordPauseEvent() {
        synchronized(counterLock) {
            val next = prefs.getInt(KEY_PAUSE_EVENTS_COUNT, 0) + 1
            prefs.edit().putInt(KEY_PAUSE_EVENTS_COUNT, next).apply()
            _pauseEventsCount.value = next
        }
    }

    private fun loadTodayBlockedCount(): Int {
        val todayKey = currentDayKey()
        val storedDay = prefs.getInt(KEY_BLOCKED_DAY, -1)
        return if (storedDay == todayKey) prefs.getInt(KEY_BLOCKED_COUNT, 0) else 0
    }

    private fun currentDayKey(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)
    }

    private fun loadProfile(): BlockingProfile {
        val raw = prefs.getString(KEY_ACTIVE_PROFILE, BlockingProfile.OFF.name)
        return try {
            BlockingProfile.valueOf(raw ?: BlockingProfile.OFF.name)
        } catch (_: Exception) {
            BlockingProfile.OFF
        }
    }

    private fun loadUpstreamProvider(): UpstreamResolverProvider {
        val raw = prefs.getString(KEY_UPSTREAM_PROVIDER, UpstreamResolverProvider.SYSTEM_DEFAULT.name)
        return try {
            UpstreamResolverProvider.valueOf(raw ?: UpstreamResolverProvider.SYSTEM_DEFAULT.name)
        } catch (_: Exception) {
            UpstreamResolverProvider.SYSTEM_DEFAULT
        }
    }

    private fun loadBlockResponseMode(): BlockResponseMode {
        val raw = prefs.getString(KEY_BLOCK_RESPONSE_MODE, BlockResponseMode.NXDOMAIN.name)
        return try {
            BlockResponseMode.valueOf(raw ?: BlockResponseMode.NXDOMAIN.name)
        } catch (_: Exception) {
            BlockResponseMode.NXDOMAIN
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

    private fun loadAllowedHashes(): Set<Long> {
        val raw = prefs.getStringSet(KEY_ALLOWED_HASHES, emptySet()) ?: emptySet()
        return raw.mapNotNull { it.toLongOrNull(16) }.toSet()
    }

    private fun saveAllowedHashes(hashes: Set<Long>) {
        prefs.edit()
            .putStringSet(KEY_ALLOWED_HASHES, hashes.map { java.lang.Long.toHexString(it) }.toSet())
            .apply()
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
        private const val KEY_UPSTREAM_PROVIDER = "upstream_provider"
        private const val KEY_BLOCK_RESPONSE_MODE = "block_response_mode"
        private const val KEY_EXCLUDE_OWN_APP = "exclude_own_app_from_vpn"
        private const val KEY_ALLOWED_HASHES = "custom_allowed_hashes"
        private const val KEY_BLOCKED_DAY = "blocked_day_key"
        private const val KEY_BLOCKED_COUNT = "blocked_today_count"
        private const val KEY_PAUSE_EVENTS_COUNT = "pause_events_count"
        private const val KEY_DNS_PROTECTION_ENABLED = "dns_protection_enabled"

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
