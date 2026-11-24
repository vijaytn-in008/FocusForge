package com.focusforge.app.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {

    /**
     * Formats seconds into MM:SS or HH:MM:SS
     */
    fun formatSecondsToTime(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val hours = safeSeconds / 3600
        val minutes = (safeSeconds % 3600) / 60
        val seconds = safeSeconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
    }

    /**
     * Formats duration in minutes to human readable string (e.g., "1h 25m" or "45m")
     */
    fun formatMinutesToDuration(minutes: Int): String {
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        val remainingMin = minutes % 60
        return if (remainingMin > 0) "${hours}h ${remainingMin}m" else "${hours}h"
    }

    /**
     * Formats epoch millis into readable date and time (e.g., "Nov 24, 09:30 AM")
     */
    fun formatEpochToDateTime(epochMillis: Long): String {
        val sdf = SimpleDateFormat("MMM d, hh:mm a", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }

    /**
     * Formats epoch millis into readable time only (e.g., "09:30 AM")
     */
    fun formatEpochToTimeOnly(epochMillis: Long): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date(epochMillis))
    }
}
