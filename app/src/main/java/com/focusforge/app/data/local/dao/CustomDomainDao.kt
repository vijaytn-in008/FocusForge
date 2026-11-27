package com.focusforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusforge.app.data.local.entity.CustomDomainEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomDomainDao {
    @Query("SELECT * FROM custom_domains ORDER BY addedAt DESC")
    fun getAllCustomDomains(): Flow<List<CustomDomainEntity>>

    @Query("SELECT domain FROM custom_domains WHERE listType = 'BLOCK'")
    fun getCustomBlockedDomains(): Flow<List<String>>

    @Query("SELECT domain FROM custom_domains WHERE listType = 'ALLOW'")
    fun getCustomAllowedDomains(): Flow<List<String>>

    @Query("SELECT domain FROM custom_domains WHERE listType = 'BLOCK'")
    suspend fun getCustomBlockedDomainsSync(): List<String>

    @Query("SELECT domain FROM custom_domains WHERE listType = 'ALLOW'")
    suspend fun getCustomAllowedDomainsSync(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(customDomain: CustomDomainEntity): Long

    @Query("DELETE FROM custom_domains WHERE domain = :domain")
    suspend fun deleteByDomain(domain: String)

    @Query("DELETE FROM custom_domains WHERE id = :id")
    suspend fun deleteById(id: Long)
}
