package med.reminder.com.data.preferences

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
        private val KEY_USER_PHOTO_URI = stringPreferencesKey("user_photo_uri")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        private val KEY_LEGAL_ACCEPTED_VERSION = intPreferencesKey("legal_accepted_version")
        private val KEY_LEGAL_ACCEPTED_AT = longPreferencesKey("legal_accepted_at")

        const val CURRENT_LEGAL_VERSION = 1
    }

    val userPhotoUri: Flow<String?> =
        context.userPrefsDataStore.data.map { it[KEY_USER_PHOTO_URI] }

    val isOnboardingCompleted: Flow<Boolean> =
        context.userPrefsDataStore.data.map { it[KEY_ONBOARDING_COMPLETED] ?: false }

    val isLegalAccepted: Flow<Boolean> =
        context.userPrefsDataStore.data.map {
            (it[KEY_LEGAL_ACCEPTED_VERSION] ?: 0) >= CURRENT_LEGAL_VERSION
        }

    val legalAcceptedAt: Flow<Long> =
        context.userPrefsDataStore.data.map { it[KEY_LEGAL_ACCEPTED_AT] ?: 0L }

    val userName: Flow<String> =
        context.userPrefsDataStore.data.map { it[KEY_USER_NAME] ?: "" }

    val userAge: Flow<Int> =
        context.userPrefsDataStore.data.map { it[KEY_USER_AGE] ?: 0 }

    val themeMode: Flow<String> =
        context.userPrefsDataStore.data.map { it[KEY_THEME_MODE] ?: "system" }

    suspend fun setThemeMode(mode: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = mode
        }
    }

    suspend fun saveUserProfile(name: String, age: Int, photoUri: String? = null) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_USER_NAME] = name
            prefs[KEY_USER_AGE] = age
            if (photoUri != null) {
                prefs[KEY_USER_PHOTO_URI] = photoUri
            }
        }
    }

    suspend fun saveUserPhoto(uri: String) {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_USER_PHOTO_URI] = uri
        }
    }

    suspend fun completeOnboarding() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETED] = true
        }
    }

    suspend fun isOnboardingDone(): Boolean =
        context.userPrefsDataStore.data.first()[KEY_ONBOARDING_COMPLETED] ?: false

    suspend fun acceptLegal() {
        context.userPrefsDataStore.edit { prefs ->
            prefs[KEY_LEGAL_ACCEPTED_VERSION] = CURRENT_LEGAL_VERSION
            prefs[KEY_LEGAL_ACCEPTED_AT] = System.currentTimeMillis()
        }
    }

    suspend fun isLegalAcceptedNow(): Boolean =
        (context.userPrefsDataStore.data.first()[KEY_LEGAL_ACCEPTED_VERSION] ?: 0) >= CURRENT_LEGAL_VERSION
}
