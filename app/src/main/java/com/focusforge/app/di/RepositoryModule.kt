package com.focusforge.app.di

import com.focusforge.app.data.repository.BlockedAppRepository
import com.focusforge.app.data.repository.BlockedAppRepositoryImpl
import com.focusforge.app.data.repository.PolicyRepository
import com.focusforge.app.data.repository.PolicyRepositoryImpl
import com.focusforge.app.data.repository.ScreenTimeRepository
import com.focusforge.app.data.repository.ScreenTimeRepositoryImpl
import com.focusforge.app.data.repository.SessionRepository
import com.focusforge.app.data.repository.SessionRepositoryImpl
import com.focusforge.app.data.repository.UserPreferencesRepository
import com.focusforge.app.data.repository.UserPreferencesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindSessionRepository(
        sessionRepositoryImpl: SessionRepositoryImpl
    ): SessionRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(
        userPreferencesRepositoryImpl: UserPreferencesRepositoryImpl
    ): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindBlockedAppRepository(
        blockedAppRepositoryImpl: BlockedAppRepositoryImpl
    ): BlockedAppRepository

    @Binds
    @Singleton
    abstract fun bindScreenTimeRepository(
        screenTimeRepositoryImpl: ScreenTimeRepositoryImpl
    ): ScreenTimeRepository

    @Binds
    @Singleton
    abstract fun bindPolicyRepository(
        policyRepositoryImpl: PolicyRepositoryImpl
    ): PolicyRepository
}
