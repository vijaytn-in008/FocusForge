package com.focusforge.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

interface UserPreferencesRepository {
    fun getUserName(): Flow<String>
    suspend fun setUserName(name: String)
    fun isOnboardingCompleted(): Flow<Boolean>
    suspend fun setOnboardingCompleted(completed: Boolean)
}

@Singleton
class UserPreferencesRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context
) : UserPreferencesRepository {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getUserName(): Flow<String> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_USER_NAME) {
                trySend(prefs.getString(KEY_USER_NAME, "").orEmpty())
            }
        }
        // Emit initial value
        trySend(prefs.getString(KEY_USER_NAME, "").orEmpty())
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override suspend fun setUserName(name: String) {
        prefs.edit().putString(KEY_USER_NAME, name.trim()).apply()
    }

    override fun isOnboardingCompleted(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_ONBOARDING_COMPLETED) {
                trySend(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
            }
        }
        // Emit initial value
        trySend(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    override suspend fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }

    companion object {
        private const val PREFS_NAME = "focusforge_user_prefs"
        private const val KEY_USER_NAME = "key_user_name"
        private const val KEY_ONBOARDING_COMPLETED = "key_onboarding_completed"
    }
}
