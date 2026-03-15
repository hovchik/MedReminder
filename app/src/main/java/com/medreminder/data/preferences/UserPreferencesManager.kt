package com.medreminder.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")

@Singleton
class UserPreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_AGE = intPreferencesKey("user_age")
    }

    val isOnboardingCompleted: Flow<Boolean> =
        context.userPrefsDataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    val userName: Flow<String> =
        context.userPrefsDataStore.data.map { it[KEY_USER_NAME] ?: "" }

    val userAge: Flow<Int> =
        context.userPrefsDataStore.data.map { it[KEY_USER_AGE] ?: 0 }

    suspend fun saveUserProfile(name: String, age: Int) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
            prefs[KEY_USER_AGE] = age
        }
    }

    suspend fun completeOnboarding() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun isOnboardingDone(): Boolean =
        context.userPrefsDataStore.data.first()[KEY_ONBOARDING_COMPLETED] ?: false
}
