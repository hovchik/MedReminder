package com.medreminder.ai.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medreminder.ai.AiProvider
import com.medreminder.ai.AiProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_preferences")

@Singleton
class AiProviderSelector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudProvider: CloudClaudeAiProvider,
    private val systemAiProvider: SystemAiProvider,
    private val customLocalProvider: CustomLocalModelProvider
) {
    companion object {
        private val KEY_SELECTED_PROVIDER = stringPreferencesKey("selected_ai_provider")
    }

    fun getSelectedProviderType(): Flow<AiProviderType> =
        context.aiPrefsDataStore.data.map { prefs ->
            val value = prefs[KEY_SELECTED_PROVIDER] ?: AiProviderType.AUTO.name
            try {
                AiProviderType.valueOf(value)
            } catch (_: Exception) {
                AiProviderType.AUTO
            }
        }

    suspend fun setSelectedProviderType(type: AiProviderType) {
        context.aiPrefsDataStore.edit { prefs ->
            prefs[KEY_SELECTED_PROVIDER] = type.name
        }
    }

    suspend fun selectProvider(): AiProvider {
        val userChoice = getSelectedProviderType().first()
        return resolveProvider(userChoice)
    }

    fun resolveProvider(requestedType: AiProviderType): AiProvider {
        return when (requestedType) {
            AiProviderType.CLOUD -> cloudProvider
            AiProviderType.SYSTEM_AI -> {
                if (systemAiProvider.isAvailable()) systemAiProvider
                else cloudProvider // fallback
            }
            AiProviderType.CUSTOM_LOCAL -> {
                if (customLocalProvider.isAvailable()) customLocalProvider
                else cloudProvider // fallback
            }
            AiProviderType.AUTO -> selectBestAvailable()
        }
    }

    private fun selectBestAvailable(): AiProvider {
        // Priority: system AI > custom local > cloud
        return when {
            systemAiProvider.isAvailable() -> systemAiProvider
            customLocalProvider.isAvailable() -> customLocalProvider
            else -> cloudProvider
        }
    }

    fun getActiveProvider(): AiProvider {
        // Synchronous version for UI display
        return selectBestAvailable()
    }

    fun getActiveProviderInfo(): ProviderInfo {
        val provider = getActiveProvider()
        return ProviderInfo(
            type = provider.type,
            displayName = provider.displayName,
            isAvailable = provider.isAvailable(),
            isLocal = provider.type != AiProviderType.CLOUD,
            privacyNote = if (provider.type != AiProviderType.CLOUD) {
                "All analysis is performed locally on your device. No data is sent to external servers."
            } else {
                "Analysis data is sent to cloud servers for processing."
            }
        )
    }

    fun getAllProviders(): List<ProviderInfo> {
        return listOf(
            ProviderInfo(
                type = AiProviderType.AUTO,
                displayName = "Auto (Recommended)",
                isAvailable = true,
                isLocal = false,
                privacyNote = "Automatically selects the best available AI engine."
            ),
            ProviderInfo(
                type = AiProviderType.SYSTEM_AI,
                displayName = systemAiProvider.displayName,
                isAvailable = systemAiProvider.isAvailable(),
                isLocal = true,
                privacyNote = "All analysis is performed locally on your device. No data is sent to external servers."
            ),
            ProviderInfo(
                type = AiProviderType.CUSTOM_LOCAL,
                displayName = customLocalProvider.displayName,
                isAvailable = customLocalProvider.isAvailable(),
                isLocal = true,
                privacyNote = "All analysis is performed locally on your device. No data is sent to external servers."
            ),
            ProviderInfo(
                type = AiProviderType.CLOUD,
                displayName = cloudProvider.displayName,
                isAvailable = cloudProvider.isAvailable(),
                isLocal = false,
                privacyNote = "Analysis data is sent to cloud servers for processing."
            )
        )
    }
}

data class ProviderInfo(
    val type: AiProviderType,
    val displayName: String,
    val isAvailable: Boolean,
    val isLocal: Boolean,
    val privacyNote: String
)
