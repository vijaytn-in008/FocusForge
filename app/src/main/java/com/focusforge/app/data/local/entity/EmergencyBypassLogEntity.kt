package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity tracking emergency bypass unlocks.
 * Records when the bypass was triggered, duration granted, and an optional reason.
 */
@Entity(tableName = "emergency_bypass_logs")
data class EmergencyBypassLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val durationSeconds: Int = 60,
    val reason: String = ""
)
