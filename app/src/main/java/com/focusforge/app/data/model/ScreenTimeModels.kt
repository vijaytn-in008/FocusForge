package com.focusforge.app.data.model

import android.graphics.drawable.Drawable

enum class AppCategoryMedium {
    SOCIAL,
    MEDIA_ENTERTAINMENT,
    PRODUCTIVITY_EDUCATION,
    GAMING,
    NEWS,
    OTHER
}

data class AppUsageStat(
    val packageName: String,
    val appName: String,
    val iconDrawable: Drawable?,
    val totalTimeForegroundMillis: Long,
    val categoryMedium: AppCategoryMedium
) {
    val totalMinutes: Int
        get() = (totalTimeForegroundMillis / (1000 * 60)).toInt()
}

data class CategoryUsageStat(
    val category: AppCategoryMedium,
    val displayName: String,
    val totalMillis: Long,
    val percentageOfTotal: Float
) {
    val totalMinutes: Int
        get() = (totalMillis / (1000 * 60)).toInt()
}

data class DailyScreenTimeSummary(
    val totalScreenTimeMillis: Long,
    val appUsageList: List<AppUsageStat>,
    val categoryBreakdown: List<CategoryUsageStat>,
    val topApp: AppUsageStat?
) {
    val totalScreenTimeMinutes: Int
        get() = (totalScreenTimeMillis / (1000 * 60)).toInt()
}
