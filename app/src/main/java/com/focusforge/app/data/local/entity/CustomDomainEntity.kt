package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_domains")
data class CustomDomainEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val listType: String, // "ALLOW" or "BLOCK"
    val addedAt: Long = System.currentTimeMillis()
)
