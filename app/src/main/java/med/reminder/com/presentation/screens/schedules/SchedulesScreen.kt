package med.reminder.com.presentation.screens.schedules

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import med.reminder.com.R
import med.reminder.com.domain.model.Medication
import med.reminder.com.domain.model.Schedule
import med.reminder.com.domain.model.ScheduleFrequency
import med.reminder.com.domain.model.DurationType
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulesScreen(
    onAddMedication: () -> Unit,
    onEditMedication: (Long) -> Unit,
    viewModel: SchedulesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }
    val totalMeds = uiState.userGroups.sumOf { it.medications.size }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMedication,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    stringResource(R.string.add_medication),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
                    Text(
                        text = stringResource(R.string.schedules),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.schedules_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Empty state
            if (totalMeds == 0 && !uiState.isLoading) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("\uD83D\uDCC5", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.no_schedules_yet),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                stringResource(R.string.no_schedules_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Grouped by user
            uiState.userGroups.forEach { group ->
                val groupKey = group.userId?.toString() ?: "self"
                val isExpanded = expandedGroups.getOrDefault(groupKey, true)

                item(key = "header_$groupKey") {
                    ScheduleUserHeader(
                        userName = group.userName,
                        isSelf = group.userId == null,
                        medicationCount = group.medications.size,
                        expanded = isExpanded,
                        onToggle = { expandedGroups[groupKey] = !isExpanded }
                    )
                }

                if (isExpanded) {
                    items(group.medications, key = { it.id }) { medication ->
                        MedicationScheduleCard(
                            medication = medication,
                            onEdit = { onEditMedication(medication.id) },
                            onToggleSchedule = { scheduleId, enabled ->
                                viewModel.toggleScheduleEnabled(medication.id, scheduleId, enabled)
                            }
                        )
                    }
                }
            }

            // Bottom spacer for FAB
            item { Spacer(modifier = Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun ScheduleUserHeader(
    userName: String,
    isSelf: Boolean,
    medicationCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val accentColor = if (isSelf) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.tertiary

    val rotationAngle by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "chevron"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        colors = CardDefaults.cardColors(
            containerColor = if (isSelf)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            else
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSelf) Icons.Default.Person else Icons.Default.Face,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = accentColor
                    )
                }
                Text(
                    text = userName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = accentColor.copy(alpha = 0.12f)
            ) {
                Text(
                    text = stringResource(R.string.medications_count_label, medicationCount),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = if (expanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { rotationZ = rotationAngle },
                tint = accentColor
            )
        }
    }
}

@Composable
private fun MedicationScheduleCard(
    medication: Medication,
    onEdit: () -> Unit,
    onToggleSchedule: (Long, Boolean) -> Unit
) {
    val pillColor = try {
        Color(medication.color.toColorInt())
    } catch (_: Exception) {
        Color(0xFF4A90D9)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Medication header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(pillColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("\uD83D\uDC8A", fontSize = 20.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (medication.dosage.isNotBlank()) {
                        Text(
                            text = "${medication.dosage} ${medication.dosageUnit}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_medication),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (medication.isEmergency) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = stringResource(R.string.emergency_includes_location),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (medication.schedules.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(8.dp))

                // Schedule list
                medication.schedules.forEachIndexed { index, schedule ->
                    if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                    ScheduleRow(
                        schedule = schedule,
                        onToggle = { enabled -> onToggleSchedule(schedule.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.AccessTime,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = schedule.timeFormatted,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = buildScheduleDescription(schedule),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = schedule.isEnabled,
            onCheckedChange = onToggle,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun buildScheduleDescription(schedule: Schedule): String {
    val freq = when (schedule.frequency) {
        ScheduleFrequency.DAILY -> stringResource(R.string.freq_daily)
        ScheduleFrequency.SPECIFIC_DAYS -> {
            val dayNames = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
            val days = schedule.daysOfWeek.mapNotNull { day ->
                dayNames.getOrNull(day - Calendar.SUNDAY)
            }
            days.joinToString(", ")
        }
        ScheduleFrequency.INTERVAL -> stringResource(R.string.every) + " ${schedule.intervalDays} " + stringResource(R.string.days)
        ScheduleFrequency.EVERY_X_HOURS -> stringResource(R.string.every) + " ${schedule.intervalHours} " + stringResource(R.string.hours)
        ScheduleFrequency.AS_NEEDED -> stringResource(R.string.freq_as_needed)
    }

    val duration = when (schedule.durationType) {
        DurationType.ONGOING -> ""
        DurationType.DAYS -> " \u2022 ${schedule.durationValue} " + stringResource(R.string.days)
        DurationType.MONTHS -> " \u2022 ${schedule.durationValue} " + stringResource(R.string.months_label)
    }

    return freq + duration
}
