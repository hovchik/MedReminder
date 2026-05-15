package med.reminder.com.ai.setupwizard

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
import med.reminder.com.ai.AiProviderType
import med.reminder.com.ai.capability.DevicePerformanceClass
import med.reminder.com.ai.capability.RecommendedAiMode
import med.reminder.com.ai.local.InstallState
import med.reminder.com.ai.modelmanager.CompatibilityTag
import med.reminder.com.ai.modelmanager.DownloadStatus
import med.reminder.com.ai.modelmanager.ModelRecommendation
import androidx.compose.ui.res.stringResource
import med.reminder.com.R

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
                    AiProviderType.CUSTOM_LOCAL -> viewModel.loadRecommendations()
                    else -> viewModel.navigateTo(WizardStep.READY)
                }
            },
            onDismiss = onDismiss
        )
        WizardStep.MODEL_INSTALL_OPTIONS -> ModelInstallOptionsScreen(
            state = state,
            onSelectRecommendation = { rec -> viewModel.selectRecommendation(rec) },
            onDownload = { viewModel.startDownload() },
            onImport = { viewModel.navigateTo(WizardStep.IMPORT_MODEL) },
            onDismiss = onDismiss
        )
        WizardStep.MODEL_DOWNLOAD -> ModelDownloadScreen(
            state = state,
            onPause = { viewModel.pauseDownload() },
            onResume = { viewModel.resumeDownload() },
            onCancel = { viewModel.cancelDownload() },
            onBackgroundDownload = { viewModel.startBackgroundDownload() },
            onComplete = { viewModel.navigateTo(WizardStep.READY) },
            onRetry = { viewModel.startDownload() },
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
            stringResource(R.string.local_ai_setup),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            stringResource(R.string.local_ai_setup_desc),
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
                    stringResource(R.string.device_check_hint),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.check_device_compatibility))
        }

        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.skip_for_now))
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
            stringResource(R.string.device_compatibility),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        if (caps != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompatibilityRow(stringResource(R.string.android_version), "API ${caps.androidVersion}", true)
                    CompatibilityRow(stringResource(R.string.aicore_gemini), if (caps.hasAiCore) stringResource(R.string.available) else stringResource(R.string.not_available), caps.hasAiCore)
                    CompatibilityRow(stringResource(R.string.ml_kit_genai), if (caps.hasMlKitGenAi) stringResource(R.string.available) else stringResource(R.string.not_available), caps.hasMlKitGenAi)
                    CompatibilityRow(stringResource(R.string.ram), "${caps.totalRamMb} MB (${caps.ramTier.displayName})", caps.ramTier.minRamMb >= 4096)
                    CompatibilityRow(stringResource(R.string.available_storage), "${caps.availableStorageMb} MB", caps.availableStorageMb > 500)
                    CompatibilityRow(stringResource(R.string.gpu_label), caps.gpuRenderer.ifBlank { stringResource(R.string.unknown) }, caps.hasGpuCompute)
                    CompatibilityRow(stringResource(R.string.cpu_cores), "${caps.cpuCoreCount}", caps.cpuCoreCount >= 4)
                    CompatibilityRow(stringResource(R.string.performance_class), caps.performanceClass.displayName,
                        caps.performanceClass >= DevicePerformanceClass.MEDIUM)
                }
            }

            if (caps.supportedRuntimes.isNotEmpty()) {
                Text(stringResource(R.string.supported_runtimes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
            Text(stringResource(R.string.onboarding_continue))
        }

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
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
            stringResource(R.string.choose_ai_mode),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            stringResource(R.string.select_ai_mode_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        val isSystemRecommended = state.recommendedMode == RecommendedAiMode.SYSTEM_AI
        val isLocalRecommended = state.recommendedMode == RecommendedAiMode.CUSTOM_LOCAL

        AiModeCard(
            title = stringResource(R.string.system_ai_title),
            description = stringResource(R.string.system_ai_desc),
            privacyNote = stringResource(R.string.privacy_local),
            icon = Icons.Default.PhoneAndroid,
            isRecommended = isSystemRecommended,
            isAvailable = state.capabilities?.hasAiCore == true || state.capabilities?.hasMlKitGenAi == true,
            onClick = { onSelectMode(AiProviderType.SYSTEM_AI) }
        )

        AiModeCard(
            title = stringResource(R.string.local_model_title),
            description = stringResource(R.string.local_model_desc),
            privacyNote = stringResource(R.string.privacy_local),
            icon = Icons.Default.Storage,
            isRecommended = isLocalRecommended,
            isAvailable = state.capabilities?.let {
                it.ramTier.minRamMb >= 4096 && it.availableStorageMb > 500
            } ?: false,
            onClick = { onSelectMode(AiProviderType.CUSTOM_LOCAL) }
        )

        AiModeCard(
            title = stringResource(R.string.cloud_ai_title),
            description = stringResource(R.string.cloud_ai_desc),
            privacyNote = stringResource(R.string.privacy_cloud),
            icon = Icons.Default.Cloud,
            isRecommended = state.recommendedMode == RecommendedAiMode.CLOUD,
            isAvailable = true,
            onClick = { onSelectMode(AiProviderType.CLOUD) }
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
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
                                label = { Text(stringResource(R.string.recommended), style = MaterialTheme.typography.labelSmall) }
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
    onSelectRecommendation: (ModelRecommendation) -> Unit,
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
        Text(stringResource(R.string.install_local_model), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            stringResource(R.string.models_ranked),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (!state.isWifiConnected) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.WifiOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.no_wifi_warning_model),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        state.recommendations.forEach { rec ->
            val isSelected = state.selectedRecommendation?.model?.modelId == rec.model.modelId
            RecommendationCard(
                recommendation = rec,
                isSelected = isSelected,
                onClick = { onSelectRecommendation(rec) }
            )
        }

        if (state.recommendations.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.no_compatible_models), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.selectedRecommendation != null) {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.download_name, state.selectedRecommendation.model.displayName))
            }
        }

        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.import_model_device))
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: ModelRecommendation,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val model = recommendation.model
    val perf = recommendation.performanceEstimate

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    model.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                CompatibilityChip(recommendation.compatibilityTag)
            }

            if (model.description.isNotBlank()) {
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(stringResource(R.string.size_mb, model.sizeMb), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.params_label, model.parameterCount), style = MaterialTheme.typography.bodySmall)
                }
                Column {
                    Text(stringResource(R.string.quant_label, model.quantization), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.format_label, model.fileFormat), style = MaterialTheme.typography.bodySmall)
                }
            }

            // Performance estimates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "~${String.format("%.0f", perf.estimatedTokensPerSec)} tok/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "~${perf.estimatedRamUsageMb} MB RAM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                if (perf.willUseGpu) {
                    Text(
                        "GPU",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            // Score bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.score_label), style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(
                    progress = { recommendation.compatibilityScore / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp),
                    color = when {
                        recommendation.compatibilityScore >= 80 -> MaterialTheme.colorScheme.primary
                        recommendation.compatibilityScore >= 60 -> MaterialTheme.colorScheme.tertiary
                        recommendation.compatibilityScore >= 40 -> MaterialTheme.colorScheme.secondary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${recommendation.compatibilityScore}/100",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
            }

            // Warnings
            recommendation.warnings.forEach { warning ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Warning,
                        null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun CompatibilityChip(tag: CompatibilityTag) {
    val (containerColor, contentColor) = when (tag) {
        CompatibilityTag.BEST_FIT -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        CompatibilityTag.RECOMMENDED -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        CompatibilityTag.COMPATIBLE -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        CompatibilityTag.MARGINAL -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        CompatibilityTag.INCOMPATIBLE -> MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.onError
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Text(
            tag.label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ModelDownloadScreen(
    state: SetupWizardState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onBackgroundDownload: () -> Unit,
    onComplete: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val ds = state.downloadState
    val progress = state.installProgress
    val modelName = state.selectedRecommendation?.model?.displayName ?: "Model"

    // WiFi warning dialog
    if (state.showWifiWarning) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.no_wifi_title)) },
            text = {
                Text(
                    stringResource(R.string.wifi_download_confirm, modelName, state.selectedRecommendation?.model?.sizeMb ?: 0)
                )
            },
            confirmButton = {
                TextButton(onClick = onRetry) {
                    Text(stringResource(R.string.download_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (ds.status) {
            DownloadStatus.CONNECTING -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    ds.message.ifBlank { stringResource(R.string.connecting) },
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                if (ds.retryCount > 0) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.retry_attempt, ds.retryCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DownloadStatus.DOWNLOADING -> {
                Text(
                    stringResource(R.string.downloading_name, modelName),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(24.dp))

                LinearProgressIndicator(
                    progress = { ds.progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "${ds.progressPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${ds.downloadedMb} / ${ds.totalMb} MB",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        String.format("%.1f MB/s", ds.speedMbPerSec),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (ds.etaSeconds > 0) {
                        Text(
                            formatEta(ds.etaSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onPause,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.pause))
                    }
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.cancel))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(onClick = onBackgroundDownload) {
                    Text(stringResource(R.string.continue_in_background))
                }
            }

            DownloadStatus.PAUSED -> {
                Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.download_paused), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ds.downloadedMb} / ${ds.totalMb} MB downloaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.resume_download))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }

            DownloadStatus.VERIFYING -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.verifying_download_ellipsis), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    "Checking file integrity",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DownloadStatus.COMPLETED -> {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.model_name_ready, modelName), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(
                    ds.message.ifBlank { "Model downloaded and verified successfully" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_continue))
                }
            }

            DownloadStatus.FAILED -> {
                Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Download Failed",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Text(
                    ds.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ds.retryCount > 0) {
                    Text(
                        "Failed after ${ds.retryCount} retries",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retry))
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }

            DownloadStatus.CANCELLED -> {
                Icon(Icons.Default.Cancel, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.download_cancelled), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.go_back))
                }
            }

            DownloadStatus.IDLE -> {
                // Fallback to install progress (for import or background download)
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
                            Text(stringResource(R.string.onboarding_continue))
                        }
                    }
                    InstallState.FAILED -> {
                        Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(progress.message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.retry))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                    else -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(stringResource(R.string.preparing), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s remaining"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s remaining"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m remaining"
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
        Text(stringResource(R.string.import_model_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Text(
            "Select a compatible model file from your device. Supported formats: .tflite, .bin, .gguf, .onnx, .task, .litertlm",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        when (progress.state) {
            InstallState.INSTALLED -> {
                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(progress.message, style = MaterialTheme.typography.titleMedium)
                Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.onboarding_continue))
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
                    Text(stringResource(R.string.try_another_file))
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
                    Text(stringResource(R.string.select_model_file))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
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

        Text(stringResource(R.string.ai_setup_complete), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

        Text(
            stringResource(R.string.ai_ready_desc),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Test prompt section
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.test_prompt), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (state.isTestingPrompt) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.running_test), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (state.testPromptResult.isNotBlank()) {
                    Text(state.testPromptResult, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(onClick = onRunTest, enabled = !state.isTestingPrompt) {
                    Text(stringResource(R.string.run_test_prompt))
                }
            }
        }

        // Benchmark section
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.benchmark), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                if (state.isRunningBenchmark) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.running_benchmark), style = MaterialTheme.typography.bodyMedium)
                    }
                }

                state.benchmarkResult?.let { result ->
                    if (result.success) {
                        Text(stringResource(R.string.inference_latency, result.inferenceLatencyMs), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.token_speed, String.format("%.1f", result.tokenGenerationSpeed)), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.memory_usage, result.memoryUsageMb), style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(stringResource(R.string.benchmark_failed, result.errorMessage ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                }

                OutlinedButton(onClick = onRunBenchmark, enabled = !state.isRunningBenchmark) {
                    Text(stringResource(R.string.run_benchmark))
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.finish_setup))
        }
    }
}
