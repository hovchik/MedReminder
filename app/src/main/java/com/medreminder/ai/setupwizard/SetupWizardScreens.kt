package com.medreminder.ai.setupwizard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.ai.AiProviderType
import com.medreminder.ai.capability.DevicePerformanceClass
import com.medreminder.ai.capability.RecommendedAiMode
import com.medreminder.ai.local.InstallState

@Composable
fun LocalAiSetupWizard(
    onDismiss: () -> Unit,
    viewModel: SetupWizardViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when (state.currentStep) {
        WizardStep.INTRO -> LocalAiIntroScreen(
            onContinue = { viewModel.detectCapabilities() },
            onDismiss = onDismiss
        )
        WizardStep.DEVICE_COMPATIBILITY -> DeviceCompatibilityScreen(
            state = state,
            onContinue = { viewModel.navigateTo(WizardStep.RECOMMENDED_MODE) },
            onDismiss = onDismiss
        )
        WizardStep.RECOMMENDED_MODE -> RecommendedAiModeScreen(
            state = state,
            onSelectMode = { type ->
                viewModel.selectAiMode(type)
                when (type) {
                    AiProviderType.CUSTOM_LOCAL -> viewModel.loadAvailableModels()
                    else -> viewModel.navigateTo(WizardStep.READY)
                }
            },
            onDismiss = onDismiss
        )
        WizardStep.MODEL_INSTALL_OPTIONS -> ModelInstallOptionsScreen(
            state = state,
            onSelectModel = { model -> viewModel.selectModel(model) },
            onDownload = { viewModel.downloadSelectedModel() },
            onImport = { viewModel.navigateTo(WizardStep.IMPORT_MODEL) },
            onDismiss = onDismiss
        )
        WizardStep.MODEL_DOWNLOAD -> ModelDownloadScreen(
            state = state,
            onComplete = { viewModel.navigateTo(WizardStep.READY) },
            onRetry = { viewModel.downloadSelectedModel() },
            onDismiss = onDismiss
        )
        WizardStep.IMPORT_MODEL -> ImportModelScreen(
            state = state,
            onImport = { uri -> viewModel.importModel(uri) },
            onComplete = { viewModel.navigateTo(WizardStep.READY) },
            onDismiss = onDismiss
        )
        WizardStep.READY -> LocalAiReadyScreen(
            state = state,
            onRunTest = { viewModel.runTestPrompt() },
            onRunBenchmark = { viewModel.runBenchmark() },
            onComplete = {
                viewModel.completeSetup()
                onDismiss()
            }
        )
    }
}

@Composable
fun LocalAiIntroScreen(
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Local AI Setup",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            "MedReminder can analyze your medication adherence using AI. " +
                    "You can choose to run AI analysis on your device for complete privacy, " +
                    "or use cloud AI for more advanced insights.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "We'll check your device capabilities and recommend the best AI mode for you.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Check Device Compatibility")
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for Now")
        }
    }
}

@Composable
fun DeviceCompatibilityScreen(
    state: SetupWizardState,
    onContinue: () -> Unit,
    onDismiss: () -> Unit
) {
    val caps = state.capabilities

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Device Compatibility",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (caps != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompatibilityRow("Android Version", "API ${caps.androidVersion}", true)
                    CompatibilityRow("AICore (Gemini Nano)", if (caps.hasAiCore) "Available" else "Not available", caps.hasAiCore)
                    CompatibilityRow("ML Kit GenAI", if (caps.hasMlKitGenAi) "Available" else "Not available", caps.hasMlKitGenAi)
                    CompatibilityRow("RAM", caps.ramTier.displayName, caps.ramTier.minRamMb >= 4096)
                    CompatibilityRow("Available Storage", "${caps.availableStorageMb} MB", caps.availableStorageMb > 500)
                    CompatibilityRow("Performance Class", caps.performanceClass.displayName,
                        caps.performanceClass >= DevicePerformanceClass.MEDIUM)
                }
            }

            if (caps.supportedRuntimes.isNotEmpty()) {
                Text("Supported Runtimes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        caps.supportedRuntimes.forEach { runtime ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(runtime, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when (state.recommendedMode) {
                        RecommendedAiMode.SYSTEM_AI -> MaterialTheme.colorScheme.primaryContainer
                        RecommendedAiMode.CUSTOM_LOCAL -> MaterialTheme.colorScheme.secondaryContainer
                        RecommendedAiMode.CLOUD -> MaterialTheme.colorScheme.tertiaryContainer
                    }
                )
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(state.deviceMessage, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun CompatibilityRow(label: String, value: String, isGood: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (isGood) Icons.Default.CheckCircle else Icons.Default.Warning,
                null,
                tint = if (isGood) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun RecommendedAiModeScreen(
    state: SetupWizardState,
    onSelectMode: (AiProviderType) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Choose AI Mode",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Select how you'd like AI analysis to run:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val isSystemRecommended = state.recommendedMode == RecommendedAiMode.SYSTEM_AI
        val isLocalRecommended = state.recommendedMode == RecommendedAiMode.CUSTOM_LOCAL

        // System AI option
        AiModeCard(
            title = "System AI",
            description = "Use your device's built-in AI engine (Gemini Nano / AICore)",
            privacyNote = "All analysis is performed locally on your device. No data is sent to external servers.",
            icon = Icons.Default.PhoneAndroid,
            isRecommended = isSystemRecommended,
            isAvailable = state.capabilities?.hasAiCore == true || state.capabilities?.hasMlKitGenAi == true,
            onClick = { onSelectMode(AiProviderType.SYSTEM_AI) }
        )

        // Custom Local Model option
        AiModeCard(
            title = "Local Model",
            description = "Download or import a compatible AI model to run on your device",
            privacyNote = "All analysis is performed locally on your device. No data is sent to external servers.",
            icon = Icons.Default.Storage,
            isRecommended = isLocalRecommended,
            isAvailable = state.capabilities?.let {
                it.ramTier.minRamMb >= 4096 && it.availableStorageMb > 500
            } ?: false,
            onClick = { onSelectMode(AiProviderType.CUSTOM_LOCAL) }
        )

        // Cloud AI option
        AiModeCard(
            title = "Cloud AI (Claude)",
            description = "Use Claude AI via the cloud for advanced analysis",
            privacyNote = "Analysis data is sent to cloud servers for processing.",
            icon = Icons.Default.Cloud,
            isRecommended = state.recommendedMode == RecommendedAiMode.CLOUD,
            isAvailable = true,
            onClick = { onSelectMode(AiProviderType.CLOUD) }
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
private fun AiModeCard(
    title: String,
    description: String,
    privacyNote: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isRecommended: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = isAvailable,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecommended) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (isRecommended) {
                            Spacer(modifier = Modifier.width(8.dp))
                            SuggestionChip(
                                onClick = {},
                                label = { Text("Recommended", style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!isAvailable) {
                    Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                }
            }
            if (isAvailable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(privacyNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ModelInstallOptionsScreen(
    state: SetupWizardState,
    onSelectModel: (com.medreminder.ai.local.LocalAiModel) -> Unit,
    onDownload: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Install Local Model", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Choose a model to install:", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

        state.availableModels.forEach { model ->
            val isSelected = state.selectedModel?.modelId == model.modelId
            Card(
                onClick = { onSelectModel(model) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(model.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Runtime: ${model.runtimeType.name} | Format: ${model.fileFormat}", style = MaterialTheme.typography.bodySmall)
                    Text("Size: ${model.sizeMb} MB | RAM: ${model.requiredRamMb}+ MB", style = MaterialTheme.typography.bodySmall)
                    Text("Quantization: ${model.quantization}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.availableModels.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Text("No compatible models found for this device", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.selectedModel != null) {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download ${state.selectedModel.displayName}")
            }
        }

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Model from Device")
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
fun ModelDownloadScreen(
    state: SetupWizardState,
    onComplete: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val progress = state.installProgress

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (progress.state) {
            InstallState.DOWNLOADING, InstallState.INSTALLING, InstallState.VALIDATING -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(progress.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                LinearProgressIndicator(
                    progress = { progress.progress },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("${(progress.progress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
            }
            InstallState.INSTALLED -> {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(progress.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
            InstallState.FAILED -> {
                Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Text(progress.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }
            else -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Preparing...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ImportModelScreen(
    state: SetupWizardState,
    onImport: (Uri) -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit
) {
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImport(uri)
        }
    }

    val progress = state.installProgress

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Import Model", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text(
            "Select a compatible model file from your device. Supported formats: .tflite, .bin, .gguf, .onnx",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        when (progress.state) {
            InstallState.INSTALLED -> {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(progress.message, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text("Continue")
                }
            }
            InstallState.INSTALLING, InstallState.VALIDATING -> {
                CircularProgressIndicator()
                Text(progress.message, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(
                    progress = { progress.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            InstallState.FAILED -> {
                Icon(Icons.Default.Error, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
                Text(progress.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Try Another File")
                }
            }
            else -> {
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Select Model File")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text("Cancel")
        }
    }
}

@Composable
fun LocalAiReadyScreen(
    state: SetupWizardState,
    onRunTest: () -> Unit,
    onRunBenchmark: () -> Unit,
    onComplete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)

        Text("AI Setup Complete", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        Text(
            "Your AI engine is ready. You can test it below or finish setup.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Test prompt section
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Test Prompt", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (state.isTestingPrompt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running test...", style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (state.testPromptResult.isNotBlank()) {
                    Text(state.testPromptResult, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(onClick = onRunTest, enabled = !state.isTestingPrompt) {
                    Text("Run Test Prompt")
                }
            }
        }

        // Benchmark section
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (state.isRunningBenchmark) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Running benchmark...", style = MaterialTheme.typography.bodyMedium)
                    }
                }

                state.benchmarkResult?.let { result ->
                    if (result.success) {
                        Text("Inference Latency: ${result.inferenceLatencyMs} ms", style = MaterialTheme.typography.bodyMedium)
                        Text("Token Speed: ${String.format("%.1f", result.tokenGenerationSpeed)} tokens/s", style = MaterialTheme.typography.bodyMedium)
                        Text("Memory Usage: ${result.memoryUsageMb} MB", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Benchmark failed: ${result.errorMessage}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedButton(onClick = onRunBenchmark, enabled = !state.isRunningBenchmark) {
                    Text("Run Benchmark")
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text("Finish Setup")
        }
    }
}
