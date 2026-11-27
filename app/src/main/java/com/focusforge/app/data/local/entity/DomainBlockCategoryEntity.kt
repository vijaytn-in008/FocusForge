package com.focusforge.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "domain_block_categories")
data class DomainBlockCategoryEntity(
    @PrimaryKey val category: String, // ADULT, DISTRACTION, DOH_ENDPOINTS
    val isEnabled: Boolean = true,
    val profileId: String = "default"
)
