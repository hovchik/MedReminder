package com.medreminder.presentation.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.alarm.AlarmScheduler
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val medCount by viewModel.medCount.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // App info card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💊", style = MaterialTheme.typography.displayMedium)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("MedReminder", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("$medCount active medications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Text("Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Notifications section
        Text("Notifications", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Notifications,
            title = "Notification settings",
            subtitle = "Manage alarm sounds and vibration",
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
                title = "Exact alarm permission",
                subtitle = "Required for reliable reminders",
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
            title = "Battery optimization",
            subtitle = "Disable for reliable alarms",
            onClick = {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                context.startActivity(intent)
            }
        )

        SettingsItem(
            icon = Icons.Default.Refresh,
            title = "Reschedule all alarms",
            subtitle = "Fix issues with missed notifications",
            onClick = { viewModel.rescheduleAllAlarms() }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Privacy section
        Text("Privacy", style = MaterialTheme.typography.titleMedium,
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
                    Text("Your data stays on your device",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text("MedReminder stores all data locally. No cloud sync, no data sharing, no accounts required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // About section
        Text("About", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(vertical = 4.dp))

        SettingsItem(
            icon = Icons.Default.Star,
            title = "Rate the app",
            subtitle = "Help us help more people",
            onClick = { /* Open Play Store */ }
        )

        SettingsItem(
            icon = Icons.Default.Share,
            title = "Share MedReminder",
            subtitle = "Help someone remember their meds",
            onClick = {
                val shareIntent = Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT,
                        "Never miss your meds again! Try MedReminder - the privacy-first medication reminder app.")
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
