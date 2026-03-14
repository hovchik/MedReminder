package com.medreminder.presentation.screens.addmed

import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.domain.model.MedicationForm
import com.medreminder.domain.model.ScheduleFrequency
import com.medreminder.presentation.theme.MedicationColors
import com.medreminder.util.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScreen(
    medicationId: Long?,
    onNavigateBack: () -> Unit,
    viewModel: AddEditMedicationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        medicationId?.let { viewModel.loadMedication(it) }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete medication?") },
            text = { Text("This will remove the medication and all its history. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteMedication(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) "Edit medication" else "Add medication") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Error
            uiState.error?.let {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text(it, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.error)
                }
            }

            // Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("Medication name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Dosage row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.dosage,
                    onValueChange = viewModel::updateDosage,
                    label = { Text("Dosage") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                var unitExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = unitExpanded,
                    onExpandedChange = { unitExpanded = it },
                    modifier = Modifier.weight(0.8f)
                ) {
                    OutlinedTextField(
                        value = uiState.dosageUnit,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Unit") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(unitExpanded) },
                        modifier = Modifier.menuAnchor(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(expanded = unitExpanded, onDismissRequest = { unitExpanded = false }) {
                        listOf("mg", "g", "ml", "mcg", "IU", "units", "drops", "puffs", "tablets").forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = { viewModel.updateDosageUnit(unit); unitExpanded = false }
                            )
                        }
                    }
                }
            }

            // Form selector
            Text("Form", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MedicationForm.entries.forEach { form ->
                    FilterChip(
                        selected = uiState.form == form,
                        onClick = { viewModel.updateForm(form) },
                        label = { Text(form.displayName) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Color picker
            Text("Color", style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MedicationColors.forEach { color ->
                    val hex = "#${Integer.toHexString(color.toArgb()).drop(2).uppercase()}"
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (uiState.color.equals(hex, true)) 3.dp else 0.dp,
                                color = MaterialTheme.colorScheme.onSurface,
                                shape = CircleShape
                            )
                            .clickable { viewModel.updateColor(hex) }
                    )
                }
            }

            HorizontalDivider()

            // Schedules
            Text("Schedules", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            uiState.schedules.forEachIndexed { index, schedule ->
                ScheduleCard(
                    schedule = schedule,
                    index = index,
                    canRemove = uiState.schedules.size > 1,
                    onTimeChange = { h, m -> viewModel.updateScheduleTime(index, h, m) },
                    onFrequencyChange = { viewModel.updateScheduleFrequency(index, it) },
                    onDaysChange = { viewModel.updateScheduleDays(index, it) },
                    onIntervalChange = { viewModel.updateScheduleInterval(index, it) },
                    onRemove = { viewModel.removeSchedule(index) }
                )
            }

            OutlinedButton(
                onClick = { viewModel.addSchedule() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add another time")
            }

            HorizontalDivider()

            // Instructions
            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = viewModel::updateInstructions,
                label = { Text("Instructions (e.g., take with food)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            // Stock tracking
            Text("Stock tracking", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.currentStock,
                    onValueChange = viewModel::updateStock,
                    label = { Text("Current stock") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = uiState.refillThreshold,
                    onValueChange = viewModel::updateRefillThreshold,
                    label = { Text("Refill at") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = uiState.refillReminder,
                    onCheckedChange = viewModel::updateRefillReminder
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Remind me to refill", style = MaterialTheme.typography.bodyLarge)
            }

            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = !uiState.isSaving,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                } else {
                    Icon(Icons.Default.Check, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (uiState.isEditing) "Save changes" else "Add medication",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun ScheduleCard(
    schedule: ScheduleInput,
    index: Int,
    canRemove: Boolean,
    onTimeChange: (Int, Int) -> Unit,
    onFrequencyChange: (ScheduleFrequency) -> Unit,
    onDaysChange: (List<Int>) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Schedule ${index + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Time picker
            OutlinedButton(
                onClick = {
                    TimePickerDialog(context, { _, h, m -> onTimeChange(h, m) },
                        schedule.hour, schedule.minute, false
                    ).show()
                },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AccessTime, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(DateUtils.formatTime(schedule.hour, schedule.minute),
                    style = MaterialTheme.typography.titleMedium)
            }

            // Frequency
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScheduleFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = schedule.frequency == freq,
                        onClick = { onFrequencyChange(freq) },
                        label = { Text(freq.displayName, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Days of week selector
            if (schedule.frequency == ScheduleFrequency.SPECIFIC_DAYS) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val days = listOf(
                        Calendar.SUNDAY to "S", Calendar.MONDAY to "M",
                        Calendar.TUESDAY to "T", Calendar.WEDNESDAY to "W",
                        Calendar.THURSDAY to "T", Calendar.FRIDAY to "F",
                        Calendar.SATURDAY to "S"
                    )
                    days.forEach { (day, label) ->
                        val selected = day in schedule.daysOfWeek
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val newDays = if (selected) schedule.daysOfWeek - day
                                else schedule.daysOfWeek + day
                                onDaysChange(newDays)
                            },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                            shape = CircleShape
                        )
                    }
                }
            }

            // Interval selector
            if (schedule.frequency == ScheduleFrequency.INTERVAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Every", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedTextField(
                        value = schedule.intervalDays.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onIntervalChange) },
                        modifier = Modifier.width(72.dp),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("days", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
