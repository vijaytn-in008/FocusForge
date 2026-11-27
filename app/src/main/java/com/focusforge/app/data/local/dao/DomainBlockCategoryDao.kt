package com.focusforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusforge.app.data.local.entity.DomainBlockCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DomainBlockCategoryDao {
    @Query("SELECT * FROM domain_block_categories")
    fun getAllCategories(): Flow<List<DomainBlockCategoryEntity>>

    @Query("SELECT * FROM domain_block_categories WHERE isEnabled = 1")
    fun getEnabledCategories(): Flow<List<DomainBlockCategoryEntity>>

    @Query("SELECT * FROM domain_block_categories WHERE isEnabled = 1")
    suspend fun getEnabledCategoriesSync(): List<DomainBlockCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(category: DomainBlockCategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<DomainBlockCategoryEntity>)

    @Query("UPDATE domain_block_categories SET isEnabled = :isEnabled WHERE category = :category")
    suspend fun setCategoryEnabled(category: String, isEnabled: Boolean)
}
