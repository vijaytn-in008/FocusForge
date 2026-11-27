package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dns_block_log")
data class DnsBlockLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val category: String,
    val timestamp: Long = System.currentTimeMillis(),
    val wasBlocked: Boolean = true
)
