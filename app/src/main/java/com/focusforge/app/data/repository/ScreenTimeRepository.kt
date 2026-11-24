package com.focusforge.app.data.repository

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.focusforge.app.data.model.AppCategoryMedium
import com.focusforge.app.data.model.AppUsageStat
import com.focusforge.app.data.model.CategoryUsageStat
import com.focusforge.app.data.model.DailyScreenTimeSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

interface ScreenTimeRepository {
    suspend fun getTodayScreenTimeSummary(): DailyScreenTimeSummary
}

@Singleton
class ScreenTimeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScreenTimeRepository {

    override suspend fun getTodayScreenTimeSummary(): DailyScreenTimeSummary = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext DailyScreenTimeSummary(0L, emptyList(), emptyList(), null)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val usageStatsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStatsList.isNullOrEmpty()) {
            return@withContext DailyScreenTimeSummary(0L, emptyList(), emptyList(), null)
        }

        val pm = context.packageManager
        // Aggregate usage stats by package name because queryUsageStats can return multiple intervals
        val aggregatedTimeMap = mutableMapOf<String, Long>()
        for (stats in usageStatsList) {
            val foreground = stats.totalTimeInForeground
            if (foreground > 0) {
                aggregatedTimeMap[stats.packageName] = (aggregatedTimeMap[stats.packageName] ?: 0L) + foreground
            }
        }

        val appUsageList = mutableListOf<AppUsageStat>()
        var totalScreenTime = 0L

        for ((pkgName, foregroundMillis) in aggregatedTimeMap) {
            // Ignore apps with less than 30 seconds of foreground time (e.g. background transitions)
            if (foregroundMillis < 30_000L) continue

            // Don't count system launcher or our own app in distraction screen time
            if (pkgName == context.packageName) continue

            val appInfo = try {
                pm.getApplicationInfo(pkgName, 0)
            } catch (_: Exception) {
                null
            }

            // Exclude system background / core framework apps
            val isSystemApp = (appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            val isCoreSystem = isCoreSystemPackage(pkgName)
            if (isSystemApp && isCoreSystem) continue

            val appName = if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else pkgName
            val iconDrawable = try {
                appInfo?.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val category = resolveAppCategory(pkgName, appInfo)

            totalScreenTime += foregroundMillis
            appUsageList.add(
                AppUsageStat(
                    packageName = pkgName,
                    appName = appName,
                    iconDrawable = iconDrawable,
                    totalTimeForegroundMillis = foregroundMillis,
                    categoryMedium = category
                )
            )
        }

        // Sort descending by screen time
        appUsageList.sortByDescending { it.totalTimeForegroundMillis }

        // Category breakdown calculation
        val categoryTotals = mutableMapOf<AppCategoryMedium, Long>()
        for (app in appUsageList) {
            categoryTotals[app.categoryMedium] = (categoryTotals[app.categoryMedium] ?: 0L) + app.totalTimeForegroundMillis
        }

        val categoryBreakdown = categoryTotals.map { (cat, millis) ->
            CategoryUsageStat(
                category = cat,
                displayName = formatCategoryName(cat),
                totalMillis = millis,
                percentageOfTotal = if (totalScreenTime > 0) millis.toFloat() / totalScreenTime.toFloat() else 0f
            )
        }.sortedByDescending { it.totalMillis }

        DailyScreenTimeSummary(
            totalScreenTimeMillis = totalScreenTime,
            appUsageList = appUsageList,
            categoryBreakdown = categoryBreakdown,
            topApp = appUsageList.firstOrNull()
        )
    }

    private fun resolveAppCategory(packageName: String, appInfo: ApplicationInfo?): AppCategoryMedium {
        val lowerPkg = packageName.lowercase()

        // 1. Keyword overrides for popular social / entertainment / productivity apps
        if (lowerPkg.contains("instagram") || lowerPkg.contains("facebook") ||
            lowerPkg.contains("twitter") || lowerPkg.contains("snapchat") ||
            lowerPkg.contains("reddit") || lowerPkg.contains("tiktok") ||
            lowerPkg.contains("threads") || lowerPkg.contains("whatsapp") ||
            lowerPkg.contains("telegram") || lowerPkg.contains("discord")
        ) {
            return AppCategoryMedium.SOCIAL
        }

        if (lowerPkg.contains("youtube") || lowerPkg.contains("netflix") ||
            lowerPkg.contains("spotify") || lowerPkg.contains("primevideo") ||
            lowerPkg.contains("hotstar") || lowerPkg.contains("twitch")
        ) {
            return AppCategoryMedium.MEDIA_ENTERTAINMENT
        }

        if (lowerPkg.contains("docs") || lowerPkg.contains("sheets") ||
            lowerPkg.contains("notion") || lowerPkg.contains("anki") ||
            lowerPkg.contains("evernote") || lowerPkg.contains("classroom") ||
            lowerPkg.contains("canvas") || lowerPkg.contains("focusforge")
        ) {
            return AppCategoryMedium.PRODUCTIVITY_EDUCATION
        }

        // 2. Android native category metadata fallback (API 26+)
        if (appInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (appInfo.category) {
                ApplicationInfo.CATEGORY_SOCIAL -> return AppCategoryMedium.SOCIAL
                ApplicationInfo.CATEGORY_VIDEO,
                ApplicationInfo.CATEGORY_AUDIO,
                ApplicationInfo.CATEGORY_IMAGE -> return AppCategoryMedium.MEDIA_ENTERTAINMENT
                ApplicationInfo.CATEGORY_GAME -> return AppCategoryMedium.GAMING
                ApplicationInfo.CATEGORY_NEWS -> return AppCategoryMedium.NEWS
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCategoryMedium.PRODUCTIVITY_EDUCATION
            }
        }

        return AppCategoryMedium.OTHER
    }

    private fun formatCategoryName(category: AppCategoryMedium): String {
        return when (category) {
            AppCategoryMedium.SOCIAL -> "Social & Messaging"
            AppCategoryMedium.MEDIA_ENTERTAINMENT -> "Video & Entertainment"
            AppCategoryMedium.PRODUCTIVITY_EDUCATION -> "Productivity & Study"
            AppCategoryMedium.GAMING -> "Gaming"
            AppCategoryMedium.NEWS -> "News & Articles"
            AppCategoryMedium.OTHER -> "Utilities & Other"
        }
    }

    private fun isCoreSystemPackage(pkgName: String): Boolean {
        return pkgName.startsWith("com.android.systemui") ||
                pkgName == "android" ||
                pkgName.contains("launcher") ||
                pkgName.contains("systemui") ||
                pkgName.contains("inputmethod")
    }
}
