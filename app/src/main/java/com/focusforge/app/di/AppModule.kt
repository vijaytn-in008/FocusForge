package com.focusforge.app.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.focusforge.app.data.local.AppDatabase
import com.focusforge.app.data.local.dao.BlockedAppAttemptDao
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.EmergencyBypassLogDao
import com.focusforge.app.data.local.dao.SessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val prefs = context.getSharedPreferences("focusforge_db_prefs", Context.MODE_PRIVATE)
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5
            )
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    prefs.edit().putBoolean("vacuum_v5_done", true).apply()
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    if (!prefs.getBoolean("vacuum_v5_done", false) && !db.inTransaction()) {
                        try {
                            db.execSQL("VACUUM")
                            prefs.edit().putBoolean("vacuum_v5_done", true).apply()
                        } catch (_: Exception) {
                            // Ignore if read-only or busy; will retry on next open
                        }
                    }
                }
            })
            .build()
    }

    @Provides
    fun provideSessionDao(database: AppDatabase): SessionDao {
        return database.sessionDao()
    }

    @Provides
    fun provideBlockedAppDao(database: AppDatabase): BlockedAppDao {
        return database.blockedAppDao()
    }

    @Provides
    fun provideEmergencyBypassLogDao(database: AppDatabase): EmergencyBypassLogDao {
        return database.emergencyBypassLogDao()
    }

    @Provides
    fun provideBlockedAppAttemptDao(database: AppDatabase): BlockedAppAttemptDao {
        return database.blockedAppAttemptDao()
    }
}
