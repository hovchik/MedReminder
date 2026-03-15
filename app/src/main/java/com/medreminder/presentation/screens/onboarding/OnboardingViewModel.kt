package com.medreminder.presentation.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.ai.capability.DeviceAiCapabilityDetector
import com.medreminder.ai.local.AiProviderSelector
import com.medreminder.ai.AiProviderType
import com.medreminder.ai.modelmanager.*
import com.medreminder.data.preferences.UserPreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val userName: String = "",
    val userAge: String = "",
    val nameError: String? = null,
    val ageError: String? = null,
    val recommendations: List<ModelRecommendation> = emptyList(),
    val selectedRecommendation: ModelRecommendation? = null,
    val bestFitRecommendation: ModelRecommendation? = null,
    val downloadState: DownloadState = DownloadState(),
    val installProgress: InstallProgress = InstallProgress(),
    val isAutoDownloading: Boolean = false,
    val showWifiWarning: Boolean = false,
    val isWifiConnected: Boolean = false,
    val onboardingComplete: Boolean = false
)

enum class OnboardingStep {
    WELCOME,
    USER_INFO,
    AI_MODEL_CHOICE,
    MODEL_SELECTION,
    AUTO_DOWNLOAD,
    DONE
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPreferencesManager: UserPreferencesManager,
    private val capabilityDetector: DeviceAiCapabilityDetector,
    private val recommendationEngine: ModelRecommendationEngine,
    private val modelInstaller: ModelInstaller,
    private val downloadManager: ModelDownloadManager,
    private val providerSelector: AiProviderSelector
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            downloadManager.downloadState.collect { ds ->
                _state.update { it.copy(downloadState = ds) }

                // When auto-downloading and completed, activate model and move to done
                if (_state.value.isAutoDownloading && ds.status == DownloadStatus.COMPLETED) {
                    val modelId = _state.value.selectedRecommendation?.model?.modelId
                    if (modelId != null) {
                        providerSelector.setActiveModelId(modelId)
                    }
                    providerSelector.setSelectedProviderType(AiProviderType.CUSTOM_LOCAL)
                    _state.update { it.copy(currentStep = OnboardingStep.DONE) }
                }
            }
        }
        viewModelScope.launch {
            modelInstaller.installProgress.collect { progress ->
                _state.update { it.copy(installProgress = progress) }
            }
        }
    }

    fun updateName(name: String) {
        _state.update { it.copy(userName = name, nameError = null) }
    }

    fun updateAge(age: String) {
        _state.update { it.copy(userAge = age, ageError = null) }
    }

    fun submitUserInfo() {
        val name = _state.value.userName.trim()
        val ageText = _state.value.userAge.trim()

        var hasError = false

        if (name.isBlank()) {
            _state.update { it.copy(nameError = "Please enter your name") }
            hasError = true
        }

        val age = ageText.toIntOrNull()
        if (age == null || age < 1 || age > 150) {
            _state.update { it.copy(ageError = "Please enter a valid age") }
            hasError = true
        }

        if (hasError) return

        viewModelScope.launch {
            userPreferencesManager.saveUserProfile(name, age!!)

            // Detect capabilities and load recommendations
            capabilityDetector.detectCapabilities()
            val recommendations = recommendationEngine.getCompatibleRecommendations()
            val bestFit = recommendationEngine.getBestRecommendation()

            _state.update {
                it.copy(
                    currentStep = OnboardingStep.AI_MODEL_CHOICE,
                    recommendations = recommendations,
                    bestFitRecommendation = bestFit,
                    selectedRecommendation = bestFit,
                    isWifiConnected = modelInstaller.isWifiAvailable()
                )
            }
        }
    }

    fun chooseAutoDownload() {
        val bestFit = _state.value.bestFitRecommendation
        if (bestFit == null) {
            // No compatible model, skip to done
            skipAiSetup()
            return
        }

        val model = bestFit.model

        // Check WiFi for large downloads
        if (model.sizeMb > 500 && !modelInstaller.isWifiAvailable()) {
            _state.update { it.copy(showWifiWarning = true) }
            return
        }

        startAutoDownload(bestFit)
    }

    fun confirmDownloadWithoutWifi() {
        val bestFit = _state.value.bestFitRecommendation ?: return
        _state.update { it.copy(showWifiWarning = false) }
        startAutoDownload(bestFit)
    }

    fun dismissWifiWarning() {
        _state.update { it.copy(showWifiWarning = false) }
    }

    private fun startAutoDownload(recommendation: ModelRecommendation) {
        _state.update {
            it.copy(
                currentStep = OnboardingStep.AUTO_DOWNLOAD,
                isAutoDownloading = true,
                selectedRecommendation = recommendation
            )
        }

        viewModelScope.launch {
            providerSelector.setSelectedProviderType(AiProviderType.CUSTOM_LOCAL)
        }

        modelInstaller.downloadModel(recommendation.model, viewModelScope)
    }

    fun chooseManualSelection() {
        _state.update { it.copy(currentStep = OnboardingStep.MODEL_SELECTION) }
    }

    fun selectRecommendation(recommendation: ModelRecommendation) {
        _state.update { it.copy(selectedRecommendation = recommendation) }
    }

    fun downloadSelectedModel() {
        val rec = _state.value.selectedRecommendation ?: return
        val model = rec.model

        if (model.sizeMb > 500 && !modelInstaller.isWifiAvailable()) {
            _state.update { it.copy(showWifiWarning = true) }
            return
        }

        _state.update {
            it.copy(
                currentStep = OnboardingStep.AUTO_DOWNLOAD,
                isAutoDownloading = true
            )
        }

        viewModelScope.launch {
            providerSelector.setSelectedProviderType(AiProviderType.CUSTOM_LOCAL)
        }

        modelInstaller.downloadModel(model, viewModelScope)
    }

    fun continueInBackground() {
        val rec = _state.value.selectedRecommendation ?: return
        modelInstaller.downloadModelInBackground(rec.model)
        finishOnboarding()
    }

    fun pauseDownload() {
        modelInstaller.pauseDownload()
    }

    fun resumeDownload() {
        val rec = _state.value.selectedRecommendation ?: return
        modelInstaller.resumeDownload(rec.model, viewModelScope)
    }

    fun cancelDownload() {
        val rec = _state.value.selectedRecommendation ?: return
        modelInstaller.cancelDownload(rec.model)
        downloadManager.resetState()
        _state.update {
            it.copy(
                currentStep = OnboardingStep.AI_MODEL_CHOICE,
                isAutoDownloading = false
            )
        }
    }

    fun retryDownload() {
        val rec = _state.value.selectedRecommendation ?: return
        downloadManager.resetState()
        modelInstaller.downloadModel(rec.model, viewModelScope)
    }

    fun skipAiSetup() {
        viewModelScope.launch {
            providerSelector.setSelectedProviderType(AiProviderType.CLOUD)
        }
        _state.update { it.copy(currentStep = OnboardingStep.DONE) }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            userPreferencesManager.completeOnboarding()
            _state.update { it.copy(onboardingComplete = true) }
        }
    }

    fun goToUserInfo() {
        _state.update { it.copy(currentStep = OnboardingStep.USER_INFO) }
    }
}
