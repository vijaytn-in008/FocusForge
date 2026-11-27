package com.focusforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusforge.app.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for session operations.
 */
@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: SessionEntity): Long

    @Query("SELECT * FROM sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime >= :startOfDay AND startTime < :endOfDay ORDER BY startTime DESC")
    fun getSessionsForDay(startOfDay: Long, endOfDay: Long): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE startTime >= :since ORDER BY startTime DESC")
    fun getSessionsSince(since: Long): Flow<List<SessionEntity>>

    @Query("SELECT SUM(durationMin) FROM sessions WHERE type = 'DEEP' AND startTime >= :since")
    fun totalDeepWorkMinutesSince(since: Long): Flow<Int?>

    @Query("SELECT SUM(durationMin) FROM sessions WHERE startTime >= :startOfDay AND startTime < :endOfDay")
    fun totalWorkMinutesForDay(startOfDay: Long, endOfDay: Long): Flow<Int?>

    @Query("SELECT SUM(durationMin) FROM sessions WHERE type = 'DEEP' AND startTime >= :startOfDay AND startTime < :endOfDay")
    fun deepWorkMinutesForDay(startOfDay: Long, endOfDay: Long): Flow<Int?>

    @Delete
    suspend fun delete(session: SessionEntity)
}
