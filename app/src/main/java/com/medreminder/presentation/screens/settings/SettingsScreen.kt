package com.medreminder.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.FileProvider
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.BuildConfig
import com.medreminder.R
import com.medreminder.ai.AiProviderType
import com.medreminder.ai.local.AiProviderSelector
import com.medreminder.ai.local.LocalAiModel
import com.medreminder.ai.local.ProviderInfo
import com.medreminder.ai.modelmanager.LocalModelManager
import com.medreminder.alarm.AlarmScheduler
import com.medreminder.data.preferences.UserPreferencesManager
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val alarmScheduler: AlarmScheduler,
    private val repository: MedicationRepository,
    private val providerSelector: AiProviderSelector,
    private val modelManager: LocalModelManager,
    private val userPreferencesManager: UserPreferencesManager
) : ViewModel() {

    val themeMode: StateFlow<String> = userPreferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val medCount = repository.getActiveMedicationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val userName: StateFlow<String> = userPreferencesManager.userName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val userAge: StateFlow<Int> = userPreferencesManager.userAge
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun saveUserProfile(name: String, age: Int) {
        viewModelScope.launch {
            userPreferencesManager.saveUserProfile(name, age)
        }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            userPreferencesManager.setThemeMode(mode)
        }
    }

    val selectedProviderType: StateFlow<AiProviderType> = providerSelector.getSelectedProviderType()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AiProviderType.AUTO)

    val installedModels = modelManager.getInstalledModelsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeModelId: StateFlow<String?> = providerSelector.getActiveModelId()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isLoadingModel = MutableStateFlow(false)
    val isLoadingModel: StateFlow<Boolean> = _isLoadingModel.asStateFlow()

    fun getAllProviders(): List<ProviderInfo> = providerSelector.getAllProviders()

    fun getActiveProviderInfo(): ProviderInfo = providerSelector.getActiveProviderInfo()

    fun setAiProvider(type: AiProviderType) {
        viewModelScope.launch {
            providerSelector.setSelectedProviderType(type)
        }
    }

    fun setActiveModel(modelId: String) {
        viewModelScope.launch {
            _isLoadingModel.value = true
            providerSelector.setActiveModelId(modelId)
            // Auto-switch to CUSTOM_LOCAL when a local model is activated
            providerSelector.setSelectedProviderType(AiProviderType.CUSTOM_LOCAL)
            _isLoadingModel.value = false
        }
    }

    fun clearActiveModel() {
        viewModelScope.launch {
            providerSelector.setActiveModelId(null)
        }
    }

    fun rescheduleAllAlarms() {
        viewModelScope.launch { alarmScheduler.scheduleAllAlarms() }
    }

    fun clearAllData(onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                repository.clearAllData()
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }

    fun exportData(onResult: (String?) -> Unit) {
        viewModelScope.launch {
            try {
                val json = repository.exportAllData()
                onResult(json)
            } catch (_: Exception) {
                onResult(null)
            }
        }
    }

    fun importData(json: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                repository.importAllData(json)
                alarmScheduler.scheduleAllAlarms()
                onResult(true)
            } catch (_: Exception) {
                onResult(false)
            }
        }
    }
}

data class LanguageOption(
    val code: String,
    val displayName: String
)

val supportedLanguages = listOf(
    LanguageOption("en", "English"),
    LanguageOption("ru", "\u0420\u0443\u0441\u0441\u043A\u0438\u0439"),
    LanguageOption("es", "Espa\u00F1ol"),
    LanguageOption("zh", "\u4E2D\u6587"),
    LanguageOption("hy", "\u0540\u0561\u0575\u0565\u0580\u0565\u0576"),
    LanguageOption("fa", "\u0641\u0627\u0631\u0633\u06CC")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAiSetup: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val medCount by viewModel.medCount.collectAsStateWithLifecycle()
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    var pendingImportJson by remember { mutableStateOf<String?>(null) }
    // Import file picker
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                // Take persistable read permission so content provider doesn't revoke access
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Not all providers support persistable permissions — transient access still works
                }
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (json != null) {
                    pendingImportJson = json
                    showImportConfirmDialog = true
                } else {
                    Toast.makeText(context, context.getString(R.string.import_error), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.import_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            onDismiss = { showLanguageDialog = false },
            onLanguageSelected = { langCode ->
                showLanguageDialog = false
                val localeList = LocaleListCompat.forLanguageTags(langCode)
                AppCompatDelegate.setApplicationLocales(localeList)
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_all_confirm_title)) },
            text = { Text(stringResource(R.string.clear_all_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAllData { success ->
                            val msg = if (success) R.string.clear_all_success else R.string.export_error
                            Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showImportConfirmDialog && pendingImportJson != null) {
        AlertDialog(
            onDismissRequest = {
                showImportConfirmDialog = false
                pendingImportJson = null
            },
            title = { Text(stringResource(R.string.import_confirm_title)) },
            text = { Text(stringResource(R.string.import_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showImportConfirmDialog = false
                        val json = pendingImportJson!!
                        pendingImportJson = null
                        viewModel.importData(json) { success ->
                            val msg = if (success) R.string.import_success else R.string.import_error
                            Toast.makeText(context, context.getString(msg), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImportConfirmDialog = false
                    pendingImportJson = null
                }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val selectedProvider by viewModel.selectedProviderType.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val activeModelId by viewModel.activeModelId.collectAsStateWithLifecycle()
    val isLoadingModel by viewModel.isLoadingModel.collectAsStateWithLifecycle()
    val activeProviderInfo = remember { viewModel.getActiveProviderInfo() }
    val allProviders = remember { viewModel.getAllProviders() }
    var showAiProviderDialog by remember { mutableStateOf(false) }
    var showModelPickerDialog by remember { mutableStateOf(false) }

    val userName by viewModel.userName.collectAsStateWithLifecycle()
    val userAge by viewModel.userAge.collectAsStateWithLifecycle()
    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()

    if (showThemeDialog) {
        ThemeDialog(
            currentTheme = themeMode,
            onDismiss = { showThemeDialog = false },
            onThemeSelected = { mode ->
                showThemeDialog = false
                viewModel.setThemeMode(mode)
            }
        )
    }

    if (showEditProfileDialog) {
        EditProfileDialog(
            currentName = userName,
            currentAge = userAge,
            onDismiss = { showEditProfileDialog = false },
            onSave = { name, age ->
                showEditProfileDialog = false
                viewModel.saveUserProfile(name, age)
            }
        )
    }

    if (showAiProviderDialog) {
        AiProviderDialog(
            currentType = selectedProvider,
            providers = allProviders,
            onDismiss = { showAiProviderDialog = false },
            onSelectProvider = { type ->
                showAiProviderDialog = false
                viewModel.setAiProvider(type)
            }
        )
    }

    if (showModelPickerDialog) {
        ActiveModelPickerDialog(
            models = installedModels,
            activeModelId = activeModelId,
            onDismiss = { showModelPickerDialog = false },
            onSelectModel = { modelId ->
                showModelPickerDialog = false
                viewModel.setActiveModel(modelId)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // App info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDC8A", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("MedReminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(stringResource(R.string.active_medications, medCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text(stringResource(R.string.version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // User section
        Text(stringResource(R.string.user_profile), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Person,
            title = if (userName.isNotBlank()) userName else stringResource(R.string.your_name),
            subtitle = if (userAge > 0) {
                stringResource(R.string.age_display, userAge)
            } else {
                stringResource(R.string.tap_to_edit_profile)
            },
            onClick = { showEditProfileDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // AI Engine section
        Text(stringResource(R.string.ai_engine), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Psychology,
            title = stringResource(R.string.ai_provider),
            subtitle = when (selectedProvider) {
                AiProviderType.AUTO -> stringResource(R.string.ai_auto_desc)
                AiProviderType.SYSTEM_AI -> stringResource(R.string.ai_system_desc)
                AiProviderType.CUSTOM_LOCAL -> stringResource(R.string.ai_local_desc)
                AiProviderType.CLOUD -> stringResource(R.string.ai_cloud_desc)
            },
            onClick = { showAiProviderDialog = true }
        )

        // Active provider info
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (activeProviderInfo.isLocal)
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (activeProviderInfo.isLocal) Icons.Default.Shield else Icons.Default.Cloud,
                        null,
                        tint = if (activeProviderInfo.isLocal) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.active_provider, activeProviderInfo.displayName),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    activeProviderInfo.privacyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (installedModels.isNotEmpty()) {
                    Text(
                        stringResource(R.string.installed_models_count, installedModels.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (installedModels.isNotEmpty()) {
            val activeModelName = installedModels.find { it.modelId == activeModelId }?.displayName
            SettingsItem(
                icon = Icons.Default.Memory,
                title = stringResource(R.string.active_local_model),
                subtitle = if (isLoadingModel) {
                    stringResource(R.string.loading_model)
                } else if (activeModelName != null) {
                    activeModelName
                } else {
                    stringResource(R.string.no_model_selected)
                },
                onClick = { showModelPickerDialog = true }
            )
        }

        SettingsItem(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.setup_local_ai),
            subtitle = stringResource(R.string.setup_local_ai_subtitle),
            onClick = onNavigateToAiSetup
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Language section
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Language,
            title = stringResource(R.string.language),
            subtitle = stringResource(R.string.language_subtitle),
            onClick = { showLanguageDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Appearance section
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Palette,
            title = stringResource(R.string.theme),
            subtitle = when (themeMode) {
                "light" -> stringResource(R.string.theme_light)
                "dark" -> stringResource(R.string.theme_dark)
                else -> stringResource(R.string.theme_system)
            },
            onClick = { showThemeDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Notifications section
        Text(stringResource(R.string.notifications), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.notification_settings),
            subtitle = stringResource(R.string.notification_settings_subtitle),
            onClick = {
                val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
                context.startActivity(intent)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SettingsItem(
                icon = Icons.Default.Alarm,
                title = stringResource(R.string.exact_alarm_permission),
                subtitle = stringResource(R.string.exact_alarm_subtitle),
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
        }

        SettingsItem(
            icon = Icons.Default.BatteryFull,
            title = stringResource(R.string.battery_optimization),
            subtitle = stringResource(R.string.battery_subtitle),
            onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        )

        SettingsItem(
            icon = Icons.Default.Refresh,
            title = stringResource(R.string.reschedule_alarms),
            subtitle = stringResource(R.string.reschedule_subtitle),
            onClick = { viewModel.rescheduleAllAlarms() }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Data management section
        Text(stringResource(R.string.data_management), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Upload,
            title = stringResource(R.string.export_data),
            subtitle = stringResource(R.string.export_data_subtitle),
            onClick = {
                viewModel.exportData { json ->
                    if (json != null) {
                        try {
                            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                            val fileName = "medreminder_backup_$timestamp.json"
                            val reportsDir = java.io.File(context.cacheDir, "reports")
                            reportsDir.mkdirs()
                            val file = java.io.File(reportsDir, fileName)
                            file.writeText(json)
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.export_data)))
                        } catch (_: Exception) {
                            Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, context.getString(R.string.export_error), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )

        SettingsItem(
            icon = Icons.Default.Download,
            title = stringResource(R.string.import_data),
            subtitle = stringResource(R.string.import_data_subtitle),
            onClick = {
                importLauncher.launch(arrayOf("application/json", "*/*"))
            }
        )

        SettingsItem(
            icon = Icons.Default.DeleteForever,
            title = stringResource(R.string.clear_all_data),
            subtitle = stringResource(R.string.clear_all_data_subtitle),
            onClick = { showClearDialog = true }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Privacy section
        Text(stringResource(R.string.privacy), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Shield, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(stringResource(R.string.data_on_device),
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.data_on_device_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // About section
        Text(stringResource(R.string.about), style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Star,
            title = stringResource(R.string.rate_app),
            subtitle = stringResource(R.string.rate_subtitle),
            onClick = { /* Open Play Store */ }
        )

        SettingsItem(
            icon = Icons.Default.Share,
            title = stringResource(R.string.share_app),
            subtitle = stringResource(R.string.share_subtitle),
            onClick = {
                val shareIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_text))
                }, "Share via")
                context.startActivity(shareIntent)
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun LanguageDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_language)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                supportedLanguages.forEach { lang ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(lang.code) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                lang.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                Icons.Default.ChevronRight,
                                null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun AiProviderDialog(
    currentType: AiProviderType,
    providers: List<ProviderInfo>,
    onDismiss: () -> Unit,
    onSelectProvider: (AiProviderType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_ai_provider)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                providers.forEach { provider ->
                    val isSelected = currentType == provider.type
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = provider.isAvailable) {
                                onSelectProvider(provider.type)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    provider.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (provider.isAvailable)
                                        MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                                if (!provider.isAvailable) {
                                    Text(
                                        stringResource(R.string.not_available_device),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

@Composable
fun ActiveModelPickerDialog(
    models: List<LocalAiModel>,
    activeModelId: String?,
    onDismiss: () -> Unit,
    onSelectModel: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_active_model)) },
        text = {
            if (models.isEmpty()) {
                Text(
                    stringResource(R.string.no_models_installed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    models.forEach { model ->
                        val isSelected = model.modelId == activeModelId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectModel(model.modelId) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected)
                                    MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        model.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "${model.parameterCount} \u2022 ${model.quantization} \u2022 ${model.sizeMb} MB",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun EditProfileDialog(
    currentName: String,
    currentAge: Int,
    onDismiss: () -> Unit,
    onSave: (String, Int) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentName) }
    var ageText by remember { mutableStateOf(if (currentAge > 0) currentAge.toString() else "") }
    var nameError by remember { mutableStateOf<String?>(null) }
    var ageError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_profile)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = null },
                    label = { Text(stringResource(R.string.your_name)) },
                    singleLine = true,
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it; ageError = null },
                    label = { Text(stringResource(R.string.your_age)) },
                    singleLine = true,
                    isError = ageError != null,
                    supportingText = ageError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    val age = ageText.trim().toIntOrNull()
                    var hasError = false

                    if (trimmedName.isBlank()) {
                        nameError = context.getString(R.string.name_required)
                        hasError = true
                    }
                    if (age == null || age < 1 || age > 150) {
                        ageError = context.getString(R.string.age_invalid)
                        hasError = true
                    }
                    if (!hasError) {
                        onSave(trimmedName, age!!)
                    }
                }
            ) {
                Text(stringResource(R.string.save_changes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
fun ThemeDialog(
    currentTheme: String,
    onDismiss: () -> Unit,
    onThemeSelected: (String) -> Unit
) {
    val themeOptions = listOf(
        Triple("system", R.string.theme_system, Icons.Default.SettingsBrightness),
        Triple("light", R.string.theme_light, Icons.Default.LightMode),
        Triple("dark", R.string.theme_dark, Icons.Default.DarkMode)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.select_theme)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                themeOptions.forEach { (mode, labelRes, icon) ->
                    val isSelected = currentTheme == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeSelected(mode) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                icon, null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                stringResource(labelRes),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}
