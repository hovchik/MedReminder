package com.medreminder.ai.local

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.medreminder.ai.AiProvider
import com.medreminder.ai.AiProviderType
import com.medreminder.ai.modelmanager.LocalModelManager
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
    private val cloudProvider: CloudAiProvider,
    private val systemAiProvider: SystemAiProvider,
    private val customLocalProvider: CustomLocalModelProvider,
    private val modelManager: LocalModelManager
) {
    companion object {
        private const val TAG = "AiProviderSelector"
        private val KEY_SELECTED_PROVIDER = stringPreferencesKey("selected_ai_provider")
        private val KEY_ACTIVE_MODEL_ID = stringPreferencesKey("active_local_model_id")
        private val KEY_CLOUD_SERVICE = stringPreferencesKey("cloud_ai_service")
        private val KEY_API_KEY_CLAUDE = stringPreferencesKey("api_key_claude")
        private val KEY_API_KEY_CHATGPT = stringPreferencesKey("api_key_chatgpt")
        private val KEY_API_KEY_DEEPSEEK = stringPreferencesKey("api_key_deepseek")
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

    /** Returns the persisted active local model ID, or null if none is set. */
    fun getActiveModelId(): Flow<String?> =
        context.aiPrefsDataStore.data.map { prefs ->
            prefs[KEY_ACTIVE_MODEL_ID]
        }

    /** Persist the selected local model as the active one, and load it into the runtime. */
    suspend fun setActiveModelId(modelId: String?) {
        context.aiPrefsDataStore.edit { prefs ->
            if (modelId != null) {
                prefs[KEY_ACTIVE_MODEL_ID] = modelId
            } else {
                prefs.remove(KEY_ACTIVE_MODEL_ID)
            }
        }
        if (modelId != null) {
            customLocalProvider.loadModel(modelId)
        } else {
            customLocalProvider.unloadModel()
        }
    }

    // --- Cloud AI service selection ---

    fun getSelectedCloudService(): Flow<CloudAiService> =
        context.aiPrefsDataStore.data.map { prefs ->
            val value = prefs[KEY_CLOUD_SERVICE] ?: CloudAiService.CLAUDE.name
            try {
                CloudAiService.valueOf(value)
            } catch (_: Exception) {
                CloudAiService.CLAUDE
            }
        }

    suspend fun setSelectedCloudService(service: CloudAiService) {
        context.aiPrefsDataStore.edit { prefs ->
            prefs[KEY_CLOUD_SERVICE] = service.name
        }
        cloudProvider.setService(service)
        // Always sync the API key for the newly selected service
        val key = getApiKeyForService(service).first()
        cloudProvider.setApiKey(key ?: "")
    }

    fun getApiKeyForService(service: CloudAiService): Flow<String?> =
        context.aiPrefsDataStore.data.map { prefs ->
            when (service) {
                CloudAiService.CLAUDE -> prefs[KEY_API_KEY_CLAUDE]
                CloudAiService.CHATGPT -> prefs[KEY_API_KEY_CHATGPT]
                CloudAiService.DEEPSEEK -> prefs[KEY_API_KEY_DEEPSEEK]
            }
        }

    suspend fun setApiKeyForService(service: CloudAiService, apiKey: String) {
        context.aiPrefsDataStore.edit { prefs ->
            val key = when (service) {
                CloudAiService.CLAUDE -> KEY_API_KEY_CLAUDE
                CloudAiService.CHATGPT -> KEY_API_KEY_CHATGPT
                CloudAiService.DEEPSEEK -> KEY_API_KEY_DEEPSEEK
            }
            if (apiKey.isBlank()) {
                prefs.remove(key)
            } else {
                prefs[key] = apiKey
            }
        }
        // If this is the active service, update the provider
        if (cloudProvider.activeService == service) {
            cloudProvider.setApiKey(apiKey)
        }
    }

    /**
     * Ensure the cloud provider is configured with the persisted service and API key.
     * Call this on app startup.
     */
    suspend fun ensureCloudProviderConfigured() {
        val service = getSelectedCloudService().first()
        cloudProvider.setService(service)
        // Always sync — clear key if none is stored for this service
        val key = getApiKeyForService(service).first()
        cloudProvider.setApiKey(key ?: "")
    }

    /**
     * Ensure the persisted active model is loaded into the runtime.
     * If no active model is set but installed models exist, auto-select the first one.
     */
    suspend fun ensureActiveModelLoaded() {
        var modelId = getActiveModelId().first()

        // If no active model ID is persisted, auto-select the first installed model
        if (modelId == null) {
            val installed = modelManager.getInstalledModelsSuspend()
            if (installed.isNotEmpty()) {
                modelId = installed.first().modelId
                Log.d(TAG, "ensureActiveModelLoaded: no active model set, auto-selecting '${modelId}'")
                setActiveModelId(modelId)
                // setActiveModelId already calls loadModel(), so we're done
                return
            }
            Log.d(TAG, "ensureActiveModelLoaded: no active model and no installed models")
            return
        }

        // Only create the adapter if none exists yet; actual model loading
        // happens lazily on first runPrompt() call inside the adapter.
        if (customLocalProvider.getActiveRuntime() == null) {
            val loaded = customLocalProvider.loadModel(modelId)
            if (!loaded) {
                Log.e(TAG, "ensureActiveModelLoaded: failed to load model '$modelId'")
            }
        }
    }

    suspend fun selectProvider(): AiProvider {
        val userChoice = getSelectedProviderType().first()
        Log.d(TAG, "selectProvider: userChoice=$userChoice")

        // Always try to ensure the local model is loaded if one is installed,
        // because SYSTEM_AI and AUTO also fall back to CUSTOM_LOCAL.
        ensureSystemAiInitialized()
        ensureActiveModelLoaded()
        if (userChoice == AiProviderType.CLOUD || userChoice == AiProviderType.AUTO) {
            ensureCloudProviderConfigured()
        }

        val provider = resolveProvider(userChoice)
        Log.d(TAG, "selectProvider: resolved to ${provider.type.name} (${provider.displayName})")
        return provider
    }

    private fun ensureSystemAiInitialized() {
        if (!systemAiProvider.isAvailable() && systemAiProvider.hasDeviceCapability()) {
            systemAiProvider.initialize()
        }
    }

    fun resolveProvider(requestedType: AiProviderType): AiProvider {
        return when (requestedType) {
            AiProviderType.CLOUD -> cloudProvider
            AiProviderType.SYSTEM_AI -> {
                if (systemAiProvider.isAvailable()) systemAiProvider
                else if (customLocalProvider.isAvailable()) {
                    Log.d(TAG, "resolveProvider: SYSTEM_AI unavailable, using CUSTOM_LOCAL")
                    customLocalProvider
                } else {
                    Log.w(TAG, "resolveProvider: SYSTEM_AI unavailable and no local model, using CLOUD")
                    cloudProvider
                }
            }
            AiProviderType.CUSTOM_LOCAL -> {
                if (customLocalProvider.isAvailable()) customLocalProvider
                else {
                    Log.w(TAG, "resolveProvider: CUSTOM_LOCAL selected but not available, falling back to AUTO")
                    selectBestAvailable()
                }
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

    fun getActiveProvider(selectedType: AiProviderType = AiProviderType.AUTO): AiProvider {
        return resolveProvider(selectedType)
    }

    fun getActiveProviderInfo(selectedType: AiProviderType = AiProviderType.AUTO): ProviderInfo {
        val provider = getActiveProvider(selectedType)
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
        val systemAiSubtitle = if (systemAiProvider.isAvailable() || systemAiProvider.hasDeviceCapability()) {
            "Uses device built-in AI engine."
        } else {
            "System AI unavailable on this device."
        }
        return listOf(
            ProviderInfo(
                type = AiProviderType.SYSTEM_AI,
                displayName = systemAiProvider.displayName,
                isAvailable = true, // Always selectable; falls back to cloud/local
                isLocal = true,
                privacyNote = systemAiSubtitle
            ),
            ProviderInfo(
                type = AiProviderType.CUSTOM_LOCAL,
                displayName = customLocalProvider.displayName,
                isAvailable = true, // Always selectable; models can be downloaded from setup wizard
                isLocal = true,
                privacyNote = "All analysis is performed locally on your device. No data is sent to external servers."
            ),
            ProviderInfo(
                type = AiProviderType.CLOUD,
                displayName = cloudProvider.displayName,
                isAvailable = true,
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
