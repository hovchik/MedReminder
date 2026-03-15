package com.medreminder.ai.setupwizard

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.ai.AiProviderType
import com.medreminder.ai.capability.DeviceAiCapabilities
import com.medreminder.ai.capability.DeviceAiCapabilityDetector
import com.medreminder.ai.capability.RecommendedAiMode
import com.medreminder.ai.local.*
import com.medreminder.ai.modelmanager.InstallProgress
import com.medreminder.ai.modelmanager.LocalModelManager
import com.medreminder.ai.modelmanager.ModelCompatibilityValidator
import com.medreminder.ai.modelmanager.ModelInstaller
import com.medreminder.ai.modelmanager.ValidationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupWizardState(
    val currentStep: WizardStep = WizardStep.INTRO,
    val capabilities: DeviceAiCapabilities? = null,
    val recommendedMode: RecommendedAiMode = RecommendedAiMode.CLOUD,
    val deviceMessage: String = "",
    val availableModels: List<LocalAiModel> = emptyList(),
    val selectedModel: LocalAiModel? = null,
    val installProgress: InstallProgress = InstallProgress(),
    val validationResult: ValidationResult? = null,
    val benchmarkResult: BenchmarkResult? = null,
    val isRunningBenchmark: Boolean = false,
    val testPromptResult: String = "",
    val isTestingPrompt: Boolean = false,
    val setupComplete: Boolean = false
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
    private val modelManager: LocalModelManager,
    private val modelInstaller: ModelInstaller,
    private val validator: ModelCompatibilityValidator,
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
                currentStep = WizardStep.DEVICE_COMPATIBILITY
            )
        }
    }

    fun loadAvailableModels() {
        val catalog = modelManager.getAvailableModelsCatalog()
        val compatibleModels = catalog.map { model ->
            val validation = validator.validate(model)
            model to validation
        }

        _state.update {
            it.copy(
                availableModels = compatibleModels.filter { (_, v) -> v.isCompatible }.map { (m, _) -> m },
                currentStep = WizardStep.MODEL_INSTALL_OPTIONS
            )
        }
    }

    fun selectModel(model: LocalAiModel) {
        val validation = validator.validate(model)
        _state.update {
            it.copy(
                selectedModel = model,
                validationResult = validation
            )
        }
    }

    fun downloadSelectedModel() {
        val model = _state.value.selectedModel ?: return
        _state.update { it.copy(currentStep = WizardStep.MODEL_DOWNLOAD) }

        viewModelScope.launch {
            modelInstaller.downloadModel(model)
        }
    }

    fun importModel(uri: Uri) {
        val model = _state.value.selectedModel ?: return

        viewModelScope.launch {
            modelInstaller.importModelFromUri(uri, model)
        }
    }

    fun runTestPrompt() {
        _state.update { it.copy(isTestingPrompt = true, testPromptResult = "") }

        viewModelScope.launch {
            try {
                val provider = providerSelector.selectProvider()
                val input = com.medreminder.ai.AnalysisInput(
                    medications = listOf(
                        com.medreminder.ai.MedicationSummary("Test Med", "10mg", "Pill", "Daily")
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
                    it.copy(
                        isTestingPrompt = false,
                        testPromptResult = result.summary
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isTestingPrompt = false,
                        testPromptResult = "Test failed: ${e.message}"
                    )
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
                    it.copy(
                        isRunningBenchmark = false,
                        benchmarkResult = result
                    )
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
