package com.focusforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusforge.app.data.local.entity.BlockedAppAttemptEntity
import kotlinx.coroutines.flow.Flow

data class AppAttemptCount(
    val packageName: String,
    val appName: String,
    val count: Int
)

@Dao
interface BlockedAppAttemptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attempt: BlockedAppAttemptEntity): Long

    @Query("SELECT * FROM blocked_app_attempts ORDER BY timestamp DESC")
    fun getAllAttempts(): Flow<List<BlockedAppAttemptEntity>>

    @Query("SELECT COUNT(*) FROM blocked_app_attempts")
    fun getTotalAttemptCount(): Flow<Int>

    @Query("SELECT packageName, appName, COUNT(*) as count FROM blocked_app_attempts GROUP BY packageName ORDER BY count DESC")
    fun getAttemptsGroupedByApp(): Flow<List<AppAttemptCount>>

    @Query("SELECT COUNT(*) FROM blocked_app_attempts WHERE timestamp >= :sinceTimestamp")
    fun getAttemptCountSince(sinceTimestamp: Long): Flow<Int>
}
