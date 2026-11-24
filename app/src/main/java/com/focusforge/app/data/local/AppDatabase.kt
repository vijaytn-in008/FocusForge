package com.focusforge.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.EmergencyBypassLogDao
import com.focusforge.app.data.local.dao.SessionDao
import com.focusforge.app.data.local.entity.BlockedAppEntity
import com.focusforge.app.data.local.entity.EmergencyBypassLogEntity
import com.focusforge.app.data.local.entity.SessionEntity

/**
 * Main Room Database for FocusForge.
 * Strictly local-first.
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

        /**
         * Migration from version 1 to 2 preserves existing sessions while creating
         * tables for blocked apps and emergency bypass logging.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `blocked_apps` (
                        `packageName` TEXT NOT NULL PRIMARY KEY,
                        `appName` TEXT NOT NULL,
                        `isBlocked` INTEGER NOT NULL,
                        `addedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `emergency_bypass_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `timestamp` INTEGER NOT NULL,
                        `durationSeconds` INTEGER NOT NULL,
                        `reason` TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
