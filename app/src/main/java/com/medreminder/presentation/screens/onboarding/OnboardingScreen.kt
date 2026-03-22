package com.medreminder.presentation.screens.onboarding

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.R
import com.medreminder.ai.modelmanager.CompatibilityTag
import com.medreminder.ai.modelmanager.DownloadStatus
import com.medreminder.ai.modelmanager.ModelRecommendation
import com.medreminder.presentation.screens.settings.supportedLanguages

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
                onContinue = { viewModel.goToPermissions() }
            )
            OnboardingStep.PERMISSIONS -> PermissionsStep(
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
                onBack = viewModel::goBackToModelChoice,
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
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    val currentLangName = supportedLanguages.find { it.code == currentLocale }?.displayName
        ?: supportedLanguages.first().displayName

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(stringResource(R.string.select_language)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    supportedLanguages.forEach { lang ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                showLanguageDialog = false
                                val localeList = LocaleListCompat.forLanguageTags(lang.code)
                                AppCompatDelegate.setApplicationLocales(localeList)
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (lang.code == currentLocale)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Text(
                                lang.displayName,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
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
        // Language chooser at the top
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            OutlinedButton(
                onClick = { showLanguageDialog = true },
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(currentLangName)
            }
        }

        Icon(
            Icons.Default.MedicalServices,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            stringResource(R.string.welcome_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            stringResource(R.string.free_features),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        // Emergency medication feature - highlighted
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Emergency,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.emergency_alerts_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.emergency_alerts_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Caregiver notifications feature
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.People,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.caregiver_notif_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.caregiver_notif_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // AI insights feature
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.ai_powered_insights),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.ai_insights_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Phone call to caregiver feature
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.phone_call_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.phone_call_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.get_started))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun PermissionsStep(onContinue: () -> Unit) {
    val context = LocalContext.current

    data class PermissionItem(
        val permission: String,
        val titleRes: Int,
        val descRes: Int,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val minSdk: Int = 0
    )

    val permissionItems = remember {
        listOf(
            PermissionItem(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else "",
                R.string.permission_notifications,
                R.string.permission_notifications_desc,
                Icons.Default.Notifications,
                Build.VERSION_CODES.TIRAMISU
            ),
            PermissionItem(
                Manifest.permission.CAMERA,
                R.string.permission_camera,
                R.string.permission_camera_desc,
                Icons.Default.CameraAlt
            ),
            PermissionItem(
                Manifest.permission.SEND_SMS,
                R.string.permission_sms,
                R.string.permission_sms_desc,
                Icons.Default.Sms
            ),
            PermissionItem(
                Manifest.permission.CALL_PHONE,
                R.string.permission_phone,
                R.string.permission_phone_desc,
                Icons.Default.Phone
            ),
            PermissionItem(
                Manifest.permission.ACCESS_FINE_LOCATION,
                R.string.permission_location,
                R.string.permission_location_desc,
                Icons.Default.LocationOn
            )
        )
    }

    // Track granted state for each permission
    var permissionStates by remember {
        mutableStateOf(
            permissionItems.map { item ->
                if (item.permission.isEmpty()) true
                else ContextCompat.checkSelfPermission(context, item.permission) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    // Single permission launcher
    var currentPermissionIndex by remember { mutableIntStateOf(-1) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (currentPermissionIndex >= 0) {
            permissionStates = permissionStates.toMutableList().also {
                it[currentPermissionIndex] = granted
            }
        }
    }

    // Multiple permissions launcher for "Grant All"
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionStates = permissionItems.mapIndexed { index, item ->
            if (item.permission.isEmpty()) true
            else results[item.permission] ?: permissionStates[index]
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            stringResource(R.string.permissions_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            stringResource(R.string.permissions_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        permissionItems.forEachIndexed { index, item ->
            if (item.minSdk == 0 || Build.VERSION.SDK_INT >= item.minSdk) {
                val isGranted = permissionStates[index]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isGranted)
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    onClick = {
                        if (!isGranted && item.permission.isNotEmpty()) {
                            currentPermissionIndex = index
                            permissionLauncher.launch(item.permission)
                        }
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (isGranted) MaterialTheme.colorScheme.secondary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(item.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(item.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isGranted) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            Text(
                                stringResource(R.string.permission_denied),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Grant All button
        val hasUngrantedPermissions = permissionItems.indices.any { index ->
            val item = permissionItems[index]
            (item.minSdk == 0 || Build.VERSION.SDK_INT >= item.minSdk) && !permissionStates[index]
        }
        if (hasUngrantedPermissions) {
            OutlinedButton(
                onClick = {
                    val permissionsToRequest = permissionItems
                        .filterIndexed { index, item ->
                            item.permission.isNotEmpty() &&
                                    (item.minSdk == 0 || Build.VERSION.SDK_INT >= item.minSdk) &&
                                    !permissionStates[index]
                        }
                        .map { it.permission }
                        .toTypedArray()
                    if (permissionsToRequest.isNotEmpty()) {
                        multiplePermissionsLauncher.launch(permissionsToRequest)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Security, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.grant_all_permissions))
            }
        }

        Text(
            stringResource(R.string.permissions_can_change),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.onboarding_continue))
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))
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
            stringResource(R.string.tell_us_about_yourself),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            stringResource(R.string.personalize_experience),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = state.userName,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.your_name)) },
            leadingIcon = { Icon(Icons.Default.Person, null) },
            isError = state.nameError != null,
            supportingText = state.nameError?.let { resId -> { Text(stringResource(resId)) } },
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
            label = { Text(stringResource(R.string.your_age)) },
            leadingIcon = { Icon(Icons.Default.Cake, null) },
            isError = state.ageError != null,
            supportingText = state.ageError?.let { resId -> { Text(stringResource(resId)) } },
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
            Text(stringResource(R.string.onboarding_continue))
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
            stringResource(R.string.ai_powered_insights),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            stringResource(R.string.onboarding_ai_description),
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
                            stringResource(R.string.best_for_device),
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
                                stringResource(R.string.gpu_accelerated),
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
                Text(stringResource(R.string.download_automatically))
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
                            stringResource(R.string.cloud_ai_recommended),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        stringResource(R.string.cloud_ai_no_compatible),
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
                Text(stringResource(R.string.choose_model_manually))
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
                    stringResource(R.string.local_ai_privacy_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.skip_use_cloud_ai))
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
            stringResource(R.string.choose_a_model),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            stringResource(R.string.models_ranked),
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
                Text(stringResource(R.string.download_model_name, state.selectedRecommendation.model.displayName))
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        TextButton(onClick = onSkip) {
            Text(stringResource(R.string.skip_for_now))
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
                    stringResource(R.string.connecting),
                    style = MaterialTheme.typography.titleMedium
                )
                if (ds.retryCount > 0) {
                    Text(
                        stringResource(R.string.retry_attempt, ds.retryCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel))
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
                    stringResource(R.string.downloading_model, modelName),
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
                        Text(stringResource(R.string.pause))
                    }
                    OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.cancel))
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onContinueInBackground) {
                    Text(stringResource(R.string.continue_bg_finish))
                }
            }

            DownloadStatus.PAUSED -> {
                Icon(Icons.Default.PauseCircle, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.download_paused), style = MaterialTheme.typography.titleMedium)
                Text(
                    "${ds.downloadedMb} / ${ds.totalMb} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onResume, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.resume))
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.cancel))
                }
            }

            DownloadStatus.VERIFYING -> {
                CircularProgressIndicator(modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.verifying_download), style = MaterialTheme.typography.titleMedium)
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
                    stringResource(R.string.model_ready, modelName),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.ai_analyze_locally),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.get_started))
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }

            DownloadStatus.FAILED -> {
                Icon(Icons.Default.Error, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.download_failed_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                Text(
                    ds.message,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.retry))
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.go_back))
                }
            }

            DownloadStatus.CANCELLED, DownloadStatus.IDLE -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.preparing), style = MaterialTheme.typography.bodyMedium)
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
            stringResource(R.string.all_set_name, state.userName),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            stringResource(R.string.start_adding_meds),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.start_using_app))
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
        title = { Text(stringResource(R.string.no_wifi_title)) },
        text = {
            Text(stringResource(R.string.no_wifi_download_warning, modelName, sizeMb))
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.download_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
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
