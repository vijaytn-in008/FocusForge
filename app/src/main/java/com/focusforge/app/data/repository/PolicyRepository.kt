package com.focusforge.app.data.repository

import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.CustomDomainDao
import com.focusforge.app.data.local.dao.DnsBlockLogDao
import com.focusforge.app.data.local.dao.DomainBlockCategoryDao
import com.focusforge.app.data.local.dao.ProfileDao
import com.focusforge.app.data.local.entity.CustomDomainEntity
import com.focusforge.app.data.local.entity.DnsBlockLogEntity
import com.focusforge.app.data.local.entity.DomainBlockCategoryEntity
import com.focusforge.app.data.local.entity.ProfileEntity
import com.focusforge.app.data.model.BlockingProfile
import com.focusforge.app.data.model.DomainCategory
import com.focusforge.app.data.model.PolicySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

interface PolicyRepository {
    val policySnapshot: StateFlow<PolicySnapshot>
    val categoryPreferences: Flow<Map<DomainCategory, Boolean>>

    suspend fun setActiveProfile(profile: BlockingProfile)
    suspend fun setCategoryEnabled(category: DomainCategory, isEnabled: Boolean)
    suspend fun addCustomDomain(domain: String, listType: String)
    suspend fun removeCustomDomain(domain: String)
    suspend fun logDnsBlock(domain: String, category: String, wasBlocked: Boolean): Long
    fun getTodayBlockedCount(): Flow<Int>
    fun getRecentDnsLogs(limit: Int = 50): Flow<List<DnsBlockLogEntity>>
    suspend fun clearAllDnsLogs()
}

@Singleton
class PolicyRepositoryImpl @Inject constructor(
    private val profileDao: ProfileDao,
    private val domainCategoryDao: DomainBlockCategoryDao,
    private val blockedAppDao: BlockedAppDao,
    private val customDomainDao: CustomDomainDao,
    private val dnsBlockLogDao: DnsBlockLogDao
) : PolicyRepository {

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    override val policySnapshot: StateFlow<PolicySnapshot> = combine(
        profileDao.getProfile(),
        domainCategoryDao.getAllCategories(),
        blockedAppDao.getBlockedPackageNames(),
        customDomainDao.getCustomBlockedDomains(),
        customDomainDao.getCustomAllowedDomains()
    ) { profileEntity, categories, blockedApps, customBlocked, customAllowed ->
        deriveSnapshot(
            profileEntity = profileEntity,
            categories = categories,
            blockedApps = blockedApps,
            customBlocked = customBlocked,
            customAllowed = customAllowed
        )
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = PolicySnapshot()
    )

    override val categoryPreferences: Flow<Map<DomainCategory, Boolean>> =
        domainCategoryDao.getAllCategories().map { list ->
            val map = list.associate {
                try {
                    DomainCategory.valueOf(it.category) to it.isEnabled
                } catch (_: Exception) {
                    null to false
                }
            }.filterKeys { it != null }.mapKeys { it.key!! }
            map
        }

    override suspend fun setActiveProfile(profile: BlockingProfile) {
        profileDao.insertOrUpdate(
            ProfileEntity(
                id = "default",
                activeProfile = profile.name,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun setCategoryEnabled(category: DomainCategory, isEnabled: Boolean) {
        domainCategoryDao.insertOrUpdate(
            DomainBlockCategoryEntity(
                category = category.name,
                isEnabled = isEnabled,
                profileId = "default"
            )
        )
    }

    override suspend fun addCustomDomain(domain: String, listType: String) {
        val normalized = domain.trim().lowercase().removePrefix("www.").removeSuffix(".")
        if (normalized.isNotBlank()) {
            customDomainDao.insert(
                CustomDomainEntity(
                    domain = normalized,
                    listType = listType.uppercase()
                )
            )
        }
    }

    override suspend fun removeCustomDomain(domain: String) {
        customDomainDao.deleteByDomain(domain.trim().lowercase())
    }

    override suspend fun logDnsBlock(domain: String, category: String, wasBlocked: Boolean): Long {
        return dnsBlockLogDao.insert(
            DnsBlockLogEntity(
                domain = domain,
                category = category,
                wasBlocked = wasBlocked
            )
        )
    }

    override fun getTodayBlockedCount(): Flow<Int> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return dnsBlockLogDao.getBlockedCountSince(calendar.timeInMillis)
    }

    override fun getRecentDnsLogs(limit: Int): Flow<List<DnsBlockLogEntity>> {
        return dnsBlockLogDao.getRecentLogs(limit)
    }

    override suspend fun clearAllDnsLogs() {
        dnsBlockLogDao.clearAll()
    }

    companion object {
        private val AUTO_APP_DOMAIN_MAP = mapOf(
            "instagram" to listOf("instagram.com", "cdninstagram.com"),
            "tiktok" to listOf("tiktok.com", "tiktokv.com", "tiktokcdn.com", "musical.ly"),
            "youtube" to listOf("youtube.com", "youtu.be", "ytimg.com"),
            "reddit" to listOf("reddit.com", "redd.it", "redditmedia.com"),
            "twitter" to listOf("twitter.com", "x.com", "twimg.com"),
            "facebook" to listOf("facebook.com", "fbcdn.net", "fb.com"),
            "snapchat" to listOf("snapchat.com", "sc-cdn.net"),
            "netflix" to listOf("netflix.com", "nflxext.com"),
            "twitch" to listOf("twitch.tv", "ttvnw.net"),
            "discord" to listOf("discord.com", "discordapp.com"),
            "pinterest" to listOf("pinterest.com", "pinimg.com")
        )

        /**
         * Pure function to derive PolicySnapshot from raw entities and settings.
         * Separated so it can be thoroughly unit tested.
         */
        fun deriveSnapshot(
            profileEntity: ProfileEntity?,
            categories: List<DomainBlockCategoryEntity>,
            blockedApps: List<String>,
            customBlocked: List<String>,
            customAllowed: List<String>
        ): PolicySnapshot {
            val profile = try {
                profileEntity?.activeProfile?.let { BlockingProfile.valueOf(it) } ?: BlockingProfile.OFF
            } catch (_: Exception) {
                BlockingProfile.OFF
            }

            val categoryMap = categories.associate { it.category to it.isEnabled }

            // Rules:
            // 1. ADULT is enabled if category toggle is true (default true)
            // 2. DOH_ENDPOINTS is enabled if category toggle is true (default true)
            val effectiveCategories = mutableSetOf<DomainCategory>()

            if (categoryMap[DomainCategory.ADULT.name] != false) {
                effectiveCategories.add(DomainCategory.ADULT)
            }
            if (categoryMap[DomainCategory.DOH_ENDPOINTS.name] != false) {
                effectiveCategories.add(DomainCategory.DOH_ENDPOINTS)
            }

            // Automatic domain mapping: Any app blocked by user automatically blocks its web counterpart
            val autoAppDomains = mutableSetOf<String>()
            for (pkg in blockedApps) {
                val lowerPkg = pkg.lowercase()
                for ((keyword, domains) in AUTO_APP_DOMAIN_MAP) {
                    if (lowerPkg.contains(keyword)) {
                        autoAppDomains.addAll(domains)
                    }
                }
            }

            val combinedBlockedDomains = (customBlocked.map { it.trim().lowercase() } + autoAppDomains).toSet()

            return PolicySnapshot(
                activeProfile = profile,
                blockedAppPackages = blockedApps.toSet(),
                enabledDomainCategories = effectiveCategories,
                customBlockedDomains = combinedBlockedDomains,
                customAllowedDomains = customAllowed.map { it.trim().lowercase() }.toSet(),
                isRestProfileActive = (profile == BlockingProfile.REST)
            )
        }
    }
}
