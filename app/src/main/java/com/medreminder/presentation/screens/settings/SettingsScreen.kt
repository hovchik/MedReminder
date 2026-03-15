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
import com.medreminder.R
import com.medreminder.alarm.AlarmScheduler
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
    private val repository: MedicationRepository
) : ViewModel() {

    val medCount = repository.getActiveMedicationCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
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
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                if (json != null) {
                    pendingImportJson = json
                    showImportConfirmDialog = true
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
                    Text(stringResource(R.string.version),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

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
