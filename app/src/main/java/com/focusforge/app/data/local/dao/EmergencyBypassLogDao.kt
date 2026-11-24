package com.focusforge.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EmergencyBypassLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: EmergencyBypassLogEntity): Long

    @Query("SELECT * FROM emergency_bypass_logs ORDER BY timestamp DESC")
    fun getAll(): Flow<List<EmergencyBypassLogEntity>>

    @Query("SELECT COUNT(*) FROM emergency_bypass_logs")
    fun getCount(): Flow<Int>
}
