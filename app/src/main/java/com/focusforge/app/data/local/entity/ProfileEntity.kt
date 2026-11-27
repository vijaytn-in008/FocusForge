package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String = "default",
    val activeProfile: String = "OFF", // OFF, RELAXED, STRICT, REST
    val updatedAt: Long = System.currentTimeMillis()
)
