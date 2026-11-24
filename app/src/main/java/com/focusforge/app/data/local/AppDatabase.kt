package com.focusforge.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.EmergencyBypassLogDao
import com.focusforge.app.data.local.dao.SessionDao
import com.focusforge.app.data.local.entity.BlockedAppEntity
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import com.focusforge.app.data.local.entity.SessionEntity

/**
 * Main Room Database for FocusForge.
 * Strictly local-first.
 * Version 2 adds BlockedAppEntity and EmergencyBypassLogEntity for Phase 2.
 */
@Database(
    entities = [
        SessionEntity::class,
        BlockedAppEntity::class,
        EmergencyBypassLogEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun blockedAppDao(): BlockedAppDao
    abstract fun emergencyBypassLogDao(): EmergencyBypassLogDao

    companion object {
        const val DATABASE_NAME = "focusforge_db"
    }
}
