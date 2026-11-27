package com.focusforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.focusforge.app.data.local.entity.DnsBlockLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DnsBlockLogDao {
    @Insert
    suspend fun insert(log: DnsBlockLogEntity): Long

    @Query("SELECT * FROM dns_block_log ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<DnsBlockLogEntity>>

    @Query("SELECT COUNT(*) FROM dns_block_log WHERE timestamp >= :sinceTimestamp AND wasBlocked = 1")
    fun getBlockedCountSince(sinceTimestamp: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM dns_block_log WHERE timestamp >= :sinceTimestamp AND wasBlocked = 1")
    suspend fun getBlockedCountSinceSync(sinceTimestamp: Long): Int

    @Query("DELETE FROM dns_block_log WHERE timestamp < :beforeTimestamp")
    suspend fun deleteLogsBefore(beforeTimestamp: Long)

    @Query("DELETE FROM dns_block_log")
    suspend fun clearAll()
}
