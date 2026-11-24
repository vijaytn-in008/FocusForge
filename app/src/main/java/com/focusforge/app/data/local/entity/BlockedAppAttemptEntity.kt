package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks every instance where the user attempted to launch an app that was actively blocked.
 */
@Entity(tableName = "blocked_app_attempts")
data class BlockedAppAttemptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val timestamp: Long = System.currentTimeMillis()
)
