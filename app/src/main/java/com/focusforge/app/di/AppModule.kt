package com.focusforge.app.di

import android.content.Context
import androidx.room.Room
import com.focusforge.app.data.local.AppDatabase
import com.focusforge.app.data.local.dao.BlockedAppAttemptDao
import com.focusforge.app.data.local.dao.BlockedAppDao
import com.focusforge.app.data.local.dao.CustomDomainDao
import com.focusforge.app.data.local.dao.DnsBlockLogDao
import com.focusforge.app.data.local.dao.DomainBlockCategoryDao
import com.focusforge.app.data.local.dao.EmergencyBypassLogDao
import com.focusforge.app.data.local.dao.ProfileDao
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
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4
            )
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

    @Provides
    fun provideProfileDao(database: AppDatabase): ProfileDao {
        return database.profileDao()
    }

    @Provides
    fun provideDomainBlockCategoryDao(database: AppDatabase): DomainBlockCategoryDao {
        return database.domainBlockCategoryDao()
    }

    @Provides
    fun provideCustomDomainDao(database: AppDatabase): CustomDomainDao {
        return database.customDomainDao()
    }

    @Provides
    fun provideDnsBlockLogDao(database: AppDatabase): DnsBlockLogDao {
        return database.dnsBlockLogDao()
    }
}
