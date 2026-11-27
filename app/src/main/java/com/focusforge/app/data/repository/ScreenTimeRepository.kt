package com.focusforge.app.data.repository

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
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
import kotlin.math.max
import kotlin.math.min

interface ScreenTimeRepository {
    suspend fun getTodayScreenTimeSummary(): DailyScreenTimeSummary
}

private data class TimeInterval(val start: Long, val end: Long)

@Singleton
class ScreenTimeRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScreenTimeRepository {

    override suspend fun getTodayScreenTimeSummary(): DailyScreenTimeSummary = withContext(Dispatchers.IO) {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return@withContext DailyScreenTimeSummary(0L, 0L, emptyList(), emptyList(), null)

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        // 1. Query raw usage events for the day to accurately reconstruct wall-clock time
        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        val screenIntervals = mutableListOf<TimeInterval>()
        var screenOn = false
        var lastScreenOnTs = startTime // Assume screen may be on if started before midnight

        val appIntervalsMap = mutableMapOf<String, MutableList<TimeInterval>>()
        val resumedAtMap = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val ts = event.timeStamp
            val pkg = event.packageName ?: continue

            when (event.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> {
                    if (!screenOn) {
                        screenOn = true
                        lastScreenOnTs = ts
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> {
                    if (screenOn) {
                        screenIntervals.add(TimeInterval(lastScreenOnTs, ts))
                        screenOn = false
                    }
                }
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    resumedAtMap[pkg] = ts
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val startTs = resumedAtMap.remove(pkg)
                    if (startTs != null && ts > startTs) {
                        appIntervalsMap.getOrPut(pkg) { mutableListOf() }.add(TimeInterval(startTs, ts))
                    }
                }
            }
        }

        // Close ongoing screen interval if currently on
        if (screenOn) {
            screenIntervals.add(TimeInterval(lastScreenOnTs, endTime))
        }

        // Close any currently resumed apps
        for ((pkg, startTs) in resumedAtMap) {
            if (endTime > startTs) {
                appIntervalsMap.getOrPut(pkg) { mutableListOf() }.add(TimeInterval(startTs, endTime))
            }
        }

        val pm = context.packageManager
        val appUsageList = mutableListOf<AppUsageStat>()
        var sumOfAllAppMillis = 0L

        // Collect all non-system app intervals to calculate the union wall-clock time
        val allValidAppIntervals = mutableListOf<TimeInterval>()

        for ((pkgName, rawIntervals) in appIntervalsMap) {
            // Exclude core system packages (include FocusForge's own screen time as requested)
            if (isCoreSystemPackage(pkgName)) continue

            val appInfo = try {
                pm.getApplicationInfo(pkgName, 0)
            } catch (_: Exception) {
                null
            }

            val isSystemApp = (appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0)
            if (isSystemApp && isCoreSystemPackage(pkgName)) continue

            // Merge overlapping intervals for this single app
            val mergedAppIntervals = mergeIntervals(rawIntervals)
            val appForegroundMillis = mergedAppIntervals.sumOf { it.end - it.start }

            // Filter out momentary transitions (< 30 seconds)
            if (appForegroundMillis < 30_000L) continue

            sumOfAllAppMillis += appForegroundMillis
            allValidAppIntervals.addAll(mergedAppIntervals)

            val appName = if (appInfo != null) pm.getApplicationLabel(appInfo).toString() else pkgName
            val iconDrawable = try {
                appInfo?.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val category = resolveAppCategory(pkgName, appInfo)

            appUsageList.add(
                AppUsageStat(
                    packageName = pkgName,
                    appName = appName,
                    iconDrawable = iconDrawable,
                    totalTimeForegroundMillis = appForegroundMillis,
                    categoryMedium = category
                )
            )
        }

        // 2. Intersect all app intervals with SCREEN_INTERACTIVE intervals
        // Ensures only moments when the screen was active AND an app was in use count
        val interactiveAppIntervals = mutableListOf<TimeInterval>()
        if (screenIntervals.isNotEmpty()) {
            for (appInt in allValidAppIntervals) {
                for (screenInt in screenIntervals) {
                    val overlapStart = max(appInt.start, screenInt.start)
                    val overlapEnd = min(appInt.end, screenInt.end)
                    if (overlapEnd > overlapStart) {
                        interactiveAppIntervals.add(TimeInterval(overlapStart, overlapEnd))
                    }
                }
            }
        } else {
            // Fallback if SCREEN_INTERACTIVE events were not present in the log window
            interactiveAppIntervals.addAll(allValidAppIntervals)
        }

        // 3. Union merge: each moment in time is counted only once!
        val mergedDeviceIntervals = mergeIntervals(interactiveAppIntervals)
        val trueDeviceWallClockMillis = mergedDeviceIntervals.sumOf { it.end - it.start }

        // Sort descending by foreground time
        appUsageList.sortByDescending { it.totalTimeForegroundMillis }

        // Category breakdown calculation based on per-app usage
        val categoryTotals = mutableMapOf<AppCategoryMedium, Long>()
        for (app in appUsageList) {
            categoryTotals[app.categoryMedium] = (categoryTotals[app.categoryMedium] ?: 0L) + app.totalTimeForegroundMillis
        }

        val categoryBreakdown = categoryTotals.map { (cat, millis) ->
            CategoryUsageStat(
                category = cat,
                displayName = formatCategoryName(cat),
                totalMillis = millis,
                percentageOfTotal = if (sumOfAllAppMillis > 0) millis.toFloat() / sumOfAllAppMillis.toFloat() else 0f
            )
        }.sortedByDescending { it.totalMillis }

        DailyScreenTimeSummary(
            totalScreenTimeMillis = trueDeviceWallClockMillis,
            appSumMillis = sumOfAllAppMillis,
            appUsageList = appUsageList,
            categoryBreakdown = categoryBreakdown,
            topApp = appUsageList.firstOrNull()
        )
    }

    private fun mergeIntervals(intervals: List<TimeInterval>): List<TimeInterval> {
        if (intervals.isEmpty()) return emptyList()
        val sorted = intervals.sortedBy { it.start }
        val merged = mutableListOf<TimeInterval>()
        var current = sorted[0]

        for (i in 1 until sorted.size) {
            val next = sorted[i]
            if (next.start <= current.end) {
                current = TimeInterval(current.start, max(current.end, next.end))
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }

    private fun resolveAppCategory(packageName: String, appInfo: ApplicationInfo?): AppCategoryMedium {
        val lowerPkg = packageName.lowercase()

        // Keyword overrides for popular social / entertainment / productivity apps
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

        // Android native category metadata fallback (API 26+)
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
