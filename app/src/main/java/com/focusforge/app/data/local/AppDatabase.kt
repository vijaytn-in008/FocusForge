package com.focusforge.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.focusforge.app.data.local.dao.SessionDao
import com.focusforge.app.data.local.entity.SessionEntity

/**
 * Main Room Database for FocusForge.
 * Strictly local-first.
 */
@Database(
    entities = [SessionEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao

    companion object {
        const val DATABASE_NAME = "focusforge_db"
    }
}
