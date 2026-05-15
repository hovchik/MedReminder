package med.reminder.com.ai.setupwizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import med.reminder.com.ai.AiProviderType
import med.reminder.com.ai.capability.DeviceAiCapabilities
import med.reminder.com.ai.capability.DeviceAiCapabilityDetector
import med.reminder.com.ai.capability.RecommendedAiMode
import med.reminder.com.ai.local.*
import med.reminder.com.ai.modelmanager.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupWizardState(
    val currentStep: WizardStep = WizardStep.INTRO,
    val capabilities: DeviceAiCapabilities? = null,
    val recommendedMode: RecommendedAiMode = RecommendedAiMode.CLOUD,
    val deviceMessage: String = "",
    val recommendations: List<ModelRecommendation> = emptyList(),
    val selectedRecommendation: ModelRecommendation? = null,
    val installProgress: InstallProgress = InstallProgress(),
    val downloadState: DownloadState = DownloadState(),
    val benchmarkResult: BenchmarkResult? = null,
    val isRunningBenchmark: Boolean = false,
    val testPromptResult: String = "",
    val isTestingPrompt: Boolean = false,
    val setupComplete: Boolean = false,
    val isWifiConnected: Boolean = false,
    val showWifiWarning: Boolean = false
)

enum class WizardStep {
    INTRO,
    DEVICE_COMPATIBILITY,
    RECOMMENDED_MODE,
    MODEL_INSTALL_OPTIONS,
    MODEL_DOWNLOAD,
    IMPORT_MODEL,
    READY
}

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val capabilityDetector: DeviceAiCapabilityDetector,
    private val modelInstaller: ModelInstaller,
    private val recommendationEngine: ModelRecommendationEngine,
    private val downloadManager: ModelDownloadManager,
    private val providerSelector: AiProviderSelector,
    private val benchmarkRunner: LocalAiBenchmarkRunner
) : ViewModel() {

    private val _state = MutableStateFlow(SetupWizardState())
    val state: StateFlow<SetupWizardState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            modelInstaller.installProgress.collect { progress ->
                _state.update { it.copy(installProgress = progress) }
            }
        }
        viewModelScope.launch {
            downloadManager.downloadState.collect { ds ->
                _state.update { it.copy(downloadState = ds) }

                // When download completes, set the model as the active one
                if (ds.status == DownloadStatus.COMPLETED) {
                    val modelId = _state.value.selectedRecommendation?.model?.modelId
                        ?: ds.modelId
                    if (modelId.isNotEmpty()) {
                        providerSelector.setActiveModelId(modelId)
                        providerSelector.setSelectedProviderType(AiProviderType.CUSTOM_LOCAL)
                    }
                }
            }
        }
    }

    fun navigateTo(step: WizardStep) {
        _state.update { it.copy(currentStep = step) }
    }

    fun detectCapabilities() {
        val caps = capabilityDetector.detectCapabilities()
        val recommended = capabilityDetector.getRecommendedMode()
        val message = capabilityDetector.getDeviceMessage()

        _state.update {
            it.copy(
                capabilities = caps,
                recommendedMode = recommended,
                deviceMessage = message,
                currentStep = WizardStep.DEVICE_COMPATIBILITY,
                isWifiConnected = modelInstaller.isWifiAvailable()
            )
        }
    }

    fun loadRecommendations() {
        val recommendations = recommendationEngine.getCompatibleRecommendations()

        // Auto-select the best one
        val best = recommendations.firstOrNull()

        _state.update {
            it.copy(
                recommendations = recommendations,
                selectedRecommendation = best,
                currentStep = WizardStep.MODEL_INSTALL_OPTIONS,
                isWifiConnected = modelInstaller.isWifiAvailable()
            )
        }
    }

    fun selectRecommendation(recommendation: ModelRecommendation) {
        _state.update { it.copy(selectedRecommendation = recommendation) }
    }

    fun startDownload() {
        val rec = _state.value.selectedRecommendation ?: return
        val model = rec.model

        _state.update { it.copy(currentStep = WizardStep.MODEL_DOWNLOAD) }

        // Check WiFi for large models
        if (model.sizeMb > 500 && !modelInstaller.isWifiAvailable()) {
            _state.update { it.copy(showWifiWarning = true) }
            return
        }

        modelInstaller.downloadModel(model, viewModelScope)
    }

    fun startDownloadAnyway() {
        val rec = _state.value.selectedRecommendation ?: return
        _state.update { it.copy(showWifiWarning = false) }
        modelInstaller.downloadModel(rec.model, viewModelScope)
    }

    fun dismissWifiWarning() {
        _state.update { it.copy(showWifiWarning = false) }
    }

    fun startBackgroundDownload() {
        val rec = _state.value.selectedRecommendation ?: return
        modelInstaller.downloadModelInBackground(rec.model)
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
        _state.update { it.copy(currentStep = WizardStep.MODEL_INSTALL_OPTIONS) }
    }

    fun importModel(uri: Uri) {
        val rec = _state.value.selectedRecommendation
        val model = rec?.model ?: LocalAiModel(
            modelId = "imported-${System.currentTimeMillis()}",
            displayName = "Imported Model",
            runtimeType = RuntimeType.MEDIA_PIPE,
            fileFormat = "bin",
            quantization = "unknown",
            requiredRamMb = 2048,
            recommendedRamMb = 4096,
            sizeMb = 0,
            localPath = "",
            installState = InstallState.NOT_INSTALLED,
            checksum = "",
            version = "1.0",
            supportsStructuredJson = false,
            supportsStreaming = false,
            supportsTextGeneration = true
        )

        viewModelScope.launch {
            modelInstaller.importModelFromUri(uri, model)
            // Activate the imported model
            providerSelector.setActiveModelId(model.modelId)
            providerSelector.setSelectedProviderType(AiProviderType.CUSTOM_LOCAL)
        }
    }

    fun runTestPrompt() {
        _state.update { it.copy(isTestingPrompt = true, testPromptResult = "") }

        viewModelScope.launch {
            try {
                val provider = providerSelector.selectProvider()
                val input = med.reminder.com.ai.AnalysisInput(
                    medications = listOf(
                        med.reminder.com.ai.MedicationSummary("Test Med", "10mg", "Pill", "Daily")
                    ),
                    adherenceRate = 90f,
                    totalDoses = 10,
                    takenDoses = 9,
                    missedDoses = 1,
                    skippedDoses = 0,
                    currentStreak = 5,
                    periodDays = 7
                )
                val result = provider.generateAnalysis(input)
                _state.update {
                    it.copy(isTestingPrompt = false, testPromptResult = result.summary)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isTestingPrompt = false, testPromptResult = "Test failed: ${e.message}")
                }
            }
        }
    }

    fun runBenchmark() {
        _state.update { it.copy(isRunningBenchmark = true, benchmarkResult = null) }

        viewModelScope.launch {
            try {
                val provider = providerSelector.selectProvider()
                val result = benchmarkRunner.runBenchmark(provider)
                _state.update {
                    it.copy(isRunningBenchmark = false, benchmarkResult = result)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isRunningBenchmark = false,
                        benchmarkResult = BenchmarkResult(
                            modelId = "unknown",
                            providerType = AiProviderType.AUTO,
                            inferenceLatencyMs = 0,
                            tokenGenerationSpeed = 0f,
                            memoryUsageMb = 0,
                            success = false,
                            errorMessage = e.message
                        )
                    )
                }
            }
        }
    }

    fun selectAiMode(type: AiProviderType) {
        viewModelScope.launch {
            providerSelector.setSelectedProviderType(type)
        }
    }

    fun completeSetup() {
        _state.update { it.copy(setupComplete = true, currentStep = WizardStep.READY) }
    }
}
