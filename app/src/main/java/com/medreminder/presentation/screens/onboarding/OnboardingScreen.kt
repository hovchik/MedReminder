package com.medreminder.presentation.screens.onboarding

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.ai.modelmanager.CompatibilityTag
import com.medreminder.ai.modelmanager.DownloadStatus
import com.medreminder.ai.modelmanager.ModelRecommendation

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.onboardingComplete) {
        if (state.onboardingComplete) {
            onOnboardingComplete()
        }
    }

    AnimatedContent(
        targetState = state.currentStep,
        transitionSpec = {
            slideInHorizontally { it } + fadeIn() togetherWith
                    slideOutHorizontally { -it } + fadeOut()
        },
        label = "onboarding_step"
    ) { step ->
        when (step) {
            OnboardingStep.WELCOME -> WelcomeStep(
                onContinue = { viewModel.goToUserInfo() }
            )
            OnboardingStep.USER_INFO -> UserInfoStep(
                state = state,
                onNameChange = viewModel::updateName,
                onAgeChange = viewModel::updateAge,
                onContinue = viewModel::submitUserInfo
            )
            OnboardingStep.AI_MODEL_CHOICE -> AiModelChoiceStep(
                state = state,
                onAutoDownload = viewModel::chooseAutoDownload,
                onManualSelect = viewModel::chooseManualSelection,
                onSkip = viewModel::skipAiSetup,
                onDismissWifiWarning = viewModel::dismissWifiWarning,
                onConfirmWithoutWifi = viewModel::confirmDownloadWithoutWifi
            )
            OnboardingStep.MODEL_SELECTION -> ManualModelSelectionStep(
                state = state,
                onSelectModel = viewModel::selectRecommendation,
                onDownload = viewModel::downloadSelectedModel,
                onBack = {},
                onSkip = viewModel::skipAiSetup,
                onDismissWifiWarning = viewModel::dismissWifiWarning,
                onConfirmWithoutWifi = viewModel::confirmDownloadWithoutWifi
            )
            OnboardingStep.AUTO_DOWNLOAD -> DownloadProgressStep(
                state = state,
                onPause = viewModel::pauseDownload,
                onResume = viewModel::resumeDownload,
                onCancel = viewModel::cancelDownload,
                onRetry = viewModel::retryDownload,
                onContinueInBackground = viewModel::continueInBackground,
                onFinish = viewModel::finishOnboarding
            )
            OnboardingStep.DONE -> DoneStep(
                state = state,
                onFinish = viewModel::finishOnboarding
            )
        }
    }
}

@Composable
private fun WelcomeStep(onContinue: () -> Unit) {
    // This step is skipped programmatically — the screen starts at USER_INFO.
    // But we render it if navigated to explicitly.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MedicalServices,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Welcome to MedReminder",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Your personal medication reminder with AI-powered insights",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
    }
}

@Composable
private fun UserInfoStep(
    state: OnboardingState,
    onNameChange: (String) -> Unit,
    onAgeChange: (String) -> Unit,
    onContinue: () -> Unit
) {
    val focusManager = LocalFocusManager.current

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
            Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "Tell us about yourself",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            "This helps us personalize your experience",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.userName,
            onValueChange = onNameChange,
            label = { Text("Your name") },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(
                onNext = { focusManager.moveFocus(FocusDirection.Down) }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.userAge,
            onValueChange = { value ->
                // Only allow digits
                if (value.all { it.isDigit() } && value.length <= 3) {
                    onAgeChange(value)
                }
            },
            label = { Text("Your age") },
            leadingIcon = { Icon(Icons.Default.Cake, null) },
            isError = state.ageError != null,
            supportingText = state.ageError?.let { { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    onContinue()
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
            enabled = state.userName.isNotBlank() && state.userAge.isNotBlank()
        ) {
            Text("Continue")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun AiModelChoiceStep(
    state: OnboardingState,
    onAutoDownload: () -> Unit,
    onManualSelect: () -> Unit,
    onSkip: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onConfirmWithoutWifi: () -> Unit
) {
    if (state.showWifiWarning) {
        WifiWarningDialog(
            modelName = state.bestFitRecommendation?.model?.displayName ?: "AI Model",
            sizeMb = state.bestFitRecommendation?.model?.sizeMb ?: 0,
            onConfirm = onConfirmWithoutWifi,
            onDismiss = onDismissWifiWarning
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            Icons.Default.Psychology,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            "AI-Powered Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            "MedReminder uses AI to analyze your medication patterns and provide personalized insights. " +
                    "You can run AI directly on your device for complete privacy.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Best fit recommendation card
        val bestFit = state.bestFitRecommendation
        if (bestFit != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Best for your device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Text(
                        bestFit.model.displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )

                    if (bestFit.model.description.isNotBlank()) {
                        Text(
                            bestFit.model.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        InfoChip("${bestFit.model.sizeMb} MB")
                        InfoChip("${bestFit.model.parameterCount} params")
                        InfoChip("Score: ${bestFit.compatibilityScore}")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            "~${String.format("%.0f", bestFit.performanceEstimate.estimatedTokensPerSec)} tok/s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (bestFit.performanceEstimate.willUseGpu) {
                            Text(
                                "GPU accelerated",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Auto download button
            Button(
                onClick = onAutoDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download automatically")
            }
        } else {
            // No compatible models
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null, tint = MaterialTheme.colorScheme.tertiary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Cloud AI recommended",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        "No compatible local AI models found for your device. " +
                                "You can use Cloud AI for medication analysis.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        // Manual selection button
        if (state.recommendations.isNotEmpty()) {
            OutlinedButton(
                onClick = onManualSelect,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Tune, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choose model manually")
            }
        }

        // Privacy note
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Local AI keeps all your health data on your device. Nothing is sent to external servers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = onSkip) {
            Text("Skip for now (use Cloud AI)")
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ManualModelSelectionStep(
    state: OnboardingState,
    onSelectModel: (ModelRecommendation) -> Unit,
    onDownload: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onDismissWifiWarning: () -> Unit,
    onConfirmWithoutWifi: () -> Unit
) {
    if (state.showWifiWarning) {
        WifiWarningDialog(
            modelName = state.selectedRecommendation?.model?.displayName ?: "AI Model",
            sizeMb = state.selectedRecommendation?.model?.sizeMb ?: 0,
            onConfirm = onConfirmWithoutWifi,
            onDismiss = onDismissWifiWarning
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "Choose a Model",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Models ranked by compatibility with your device:",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        state.recommendations.forEach { rec ->
            val isSelected = state.selectedRecommendation?.model?.modelId == rec.model.modelId
            ModelCard(
                recommendation = rec,
                isSelected = isSelected,
                onClick = { onSelectModel(rec) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (state.selectedRecommendation != null) {
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Download ${state.selectedRecommendation.model.displayName}")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = onSkip) {
            Text("Skip for now")
        }
    }
}

@Composable
private fun ModelCard(
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                TagChip(recommendation.compatibilityTag)
            }

            if (model.description.isNotBlank()) {
                Text(
                    model.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${model.sizeMb} MB | ${model.parameterCount}", style = MaterialTheme.typography.bodySmall)
                Text(
                    "~${String.format("%.0f", perf.estimatedTokensPerSec)} tok/s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Score bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { recommendation.compatibilityScore / 100f },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp),
                    color = when {
                        recommendation.compatibilityScore >= 80 -> MaterialTheme.colorScheme.primary
                        recommendation.compatibilityScore >= 60 -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.error
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "${recommendation.compatibilityScore}",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun TagChip(tag: CompatibilityTag) {
    val color = when (tag) {
        CompatibilityTag.BEST_FIT -> MaterialTheme.colorScheme.primaryContainer
        CompatibilityTag.RECOMMENDED -> MaterialTheme.colorScheme.secondaryContainer
        CompatibilityTag.COMPATIBLE -> MaterialTheme.colorScheme.tertiaryContainer
        CompatibilityTag.MARGINAL -> MaterialTheme.colorScheme.errorContainer
        CompatibilityTag.INCOMPATIBLE -> MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(12.dp), color = color) {
        Text(
            tag.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun DownloadProgressStep(
    state: OnboardingState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onContinueInBackground: () -> Unit,
    onFinish: () -> Unit
) {
    val ds = state.downloadState
    val modelName = state.selectedRecommendation?.model?.displayName ?: "AI Model"

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
                    "Connecting...",
                    style = MaterialTheme.typography.titleMedium
                )
                if (ds.retryCount > 0) {
                    Text(
                        "Retry attempt ${ds.retryCount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }

            DownloadStatus.DOWNLOADING -> {
                Icon(
                    Icons.Default.Download,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Downloading $modelName",
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
                    Text("${ds.progressPercent}%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("${ds.downloadedMb} / ${ds.totalMb} MB", style = MaterialTheme.typography.bodyMedium)
                }

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

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Pause, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onContinueInBackground) {
                    Text("Continue in background & finish setup")
                }
            }

            DownloadStatus.PAUSED -> {
                Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Download Paused", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ds.downloadedMb} / ${ds.totalMb} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Resume")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Cancel")
                }
            }

            DownloadStatus.VERIFYING -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text("Verifying download...", style = MaterialTheme.typography.titleMedium)
            }

            DownloadStatus.COMPLETED -> {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "$modelName is ready!",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    "AI will analyze your medication patterns locally on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text("Get Started")
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }

            DownloadStatus.FAILED -> {
                Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Text("Download Failed", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    ds.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text("Retry")
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("Go Back")
                }
            }

            DownloadStatus.CANCELLED, DownloadStatus.IDLE -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Preparing...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DoneStep(
    state: OnboardingState,
    onFinish: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Celebration,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "You're all set, ${state.userName}!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            "Start adding your medications and MedReminder will help you stay on track.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Using MedReminder")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun WifiWarningDialog(
    modelName: String,
    sizeMb: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.WifiOff, null) },
        title = { Text("No WiFi Connection") },
        text = {
            Text(
                "Downloading $modelName ($sizeMb MB) will use mobile data. " +
                        "We recommend connecting to WiFi first."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Download Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatEta(seconds: Long): String {
    return when {
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }
}
