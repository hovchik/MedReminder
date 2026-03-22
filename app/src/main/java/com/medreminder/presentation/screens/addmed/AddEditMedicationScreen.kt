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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medreminder.R
import com.medreminder.domain.model.DurationType
import com.medreminder.domain.model.MedicationForm
import com.medreminder.domain.model.ScheduleFrequency
import com.medreminder.presentation.theme.MedicationColors
import com.medreminder.presentation.util.translatedName
import com.medreminder.util.DateUtils
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScreen(
    medicationId: Long?,
    onNavigateBack: () -> Unit,
    onScanMedication: () -> Unit = {},
    onViewAnalysis: (Long) -> Unit = {},
    scannedName: String? = null,
    scannedDosage: String? = null,
    viewModel: AddEditMedicationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        medicationId?.let { viewModel.loadMedication(it) }
    }

    LaunchedEffect(scannedName, scannedDosage) {
        if (!scannedName.isNullOrBlank()) viewModel.updateName(scannedName)
        if (!scannedDosage.isNullOrBlank()) {
            // Try to parse dosage and unit from scanned text (e.g., "500mg" -> "500", "mg")
            val regex = Regex("""(\d+\.?\d*)\s*(mg|g|ml|mcg|IU|units|drops|puffs|tablets)?""", RegexOption.IGNORE_CASE)
            val match = regex.find(scannedDosage)
            if (match != null) {
                viewModel.updateDosage(match.groupValues[1])
                if (match.groupValues[2].isNotBlank()) {
                    viewModel.updateDosageUnit(match.groupValues[2].lowercase())
                }
            } else {
                viewModel.updateDosage(scannedDosage)
            }
        }
    }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onNavigateBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_medication_title)) },
            text = { Text(stringResource(R.string.delete_medication_message)) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteMedication(); showDeleteDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (uiState.isEditing) stringResource(R.string.edit_medication) else stringResource(R.string.add_medication)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    if (uiState.isEditing) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
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

            // OCR scan button
            if (!uiState.isEditing) {
                OutlinedButton(
                    onClick = onScanMedication,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.scan_with_camera))
                }
            }

            // Medication for (person selector)
            MedicationAssigneeSelector(
                assignedToId = uiState.assignedToId,
                assignedToName = uiState.assignedToName,
                familyMembers = uiState.familyMembers,
                onAssignedToChange = { id, name -> viewModel.updateAssignedTo(id, name) }
            )

            // Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text(stringResource(R.string.medication_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Dosage row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.dosage,
                    onValueChange = viewModel::updateDosage,
                    label = { Text(stringResource(R.string.dosage)) },
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
                        label = { Text(stringResource(R.string.unit)) },
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
            Text(stringResource(R.string.form), style = MaterialTheme.typography.labelLarge)
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
                        label = { Text(form.translatedName()) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Color picker
            Text(stringResource(R.string.color), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MedicationColors.forEach { color ->
                    val hex = "#${String.format("%06X", color.toArgb() and 0xFFFFFF)}"
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
            Text(stringResource(R.string.schedules), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            uiState.schedules.forEachIndexed { index, schedule ->
                ScheduleCard(
                    schedule = schedule,
                    index = index,
                    canRemove = uiState.schedules.size > 1,
                    onTimeChange = { h, m -> viewModel.updateScheduleTime(index, h, m) },
                    onFrequencyChange = { viewModel.updateScheduleFrequency(index, it) },
                    onDaysChange = { viewModel.updateScheduleDays(index, it) },
                    onIntervalChange = { viewModel.updateScheduleInterval(index, it) },
                    onIntervalHoursChange = { viewModel.updateScheduleIntervalHours(index, it) },
                    onToleranceChange = { viewModel.updateScheduleToleranceMinutes(index, it) },
                    onDurationTypeChange = { viewModel.updateScheduleDurationType(index, it) },
                    onDurationValueChange = { viewModel.updateScheduleDurationValue(index, it) },
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
                Text(stringResource(R.string.add_another_time))
            }

            HorizontalDivider()

            // Instructions
            OutlinedTextField(
                value = uiState.instructions,
                onValueChange = viewModel::updateInstructions,
                label = { Text(stringResource(R.string.instructions_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            // Stock tracking
            Text(stringResource(R.string.stock_tracking), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uiState.currentStock,
                    onValueChange = viewModel::updateStock,
                    label = { Text(stringResource(R.string.current_stock)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = uiState.refillThreshold,
                    onValueChange = viewModel::updateRefillThreshold,
                    label = { Text(stringResource(R.string.refill_at)) },
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
                Text(stringResource(R.string.remind_to_refill), style = MaterialTheme.typography.bodyLarge)
            }

            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text(stringResource(R.string.notes)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                shape = RoundedCornerShape(12.dp)
            )

            HorizontalDivider()

            // Caregiver notifications
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.notify_caregivers),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        stringResource(R.string.notify_caregivers_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.notifyCaregivers,
                    onCheckedChange = viewModel::updateNotifyCaregivers
                )
            }

            // Emergency medication
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.emergency_medication),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        stringResource(R.string.emergency_medication_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = uiState.isEmergency,
                    onCheckedChange = viewModel::updateIsEmergency,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.error,
                        checkedTrackColor = MaterialTheme.colorScheme.errorContainer
                    )
                )
            }

            // AI Analysis button (only shown when editing an existing medication)
            if (uiState.isEditing && medicationId != null) {
                OutlinedButton(
                    onClick = { onViewAnalysis(medicationId) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Default.Psychology, null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.view_ai_analysis),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

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
                        if (uiState.isEditing) stringResource(R.string.save_changes) else stringResource(R.string.add_medication),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationAssigneeSelector(
    assignedToId: Long?,
    assignedToName: String,
    familyMembers: List<com.medreminder.domain.model.FamilyMember>,
    onAssignedToChange: (Long?, String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayName = if (assignedToId == null) stringResource(R.string.myself) else assignedToName

    Text(stringResource(R.string.medication_for), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(
                    if (assignedToId == null) Icons.Default.Person else Icons.Default.FamilyRestroom,
                    null
                )
            }
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.myself))
                    }
                },
                onClick = { onAssignedToChange(null, ""); expanded = false }
            )
            if (familyMembers.isNotEmpty()) {
                HorizontalDivider()
            }
            familyMembers.forEach { member ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FamilyRestroom, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(member.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${member.relation} - ${member.age} yrs",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    onClick = { onAssignedToChange(member.id, member.name); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleCard(
    schedule: ScheduleInput,
    index: Int,
    canRemove: Boolean,
    onTimeChange: (Int, Int) -> Unit,
    onFrequencyChange: (ScheduleFrequency) -> Unit,
    onDaysChange: (List<Int>) -> Unit,
    onIntervalChange: (Int) -> Unit,
    onIntervalHoursChange: (Int) -> Unit,
    onToleranceChange: (Int) -> Unit,
    onDurationTypeChange: (DurationType) -> Unit,
    onDurationValueChange: (Int) -> Unit,
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
                Text(stringResource(R.string.schedule_number, index + 1), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, stringResource(R.string.remove), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Time picker (start time for EVERY_X_HOURS, or dose time for others)
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
                val timeLabel = if (schedule.frequency == ScheduleFrequency.EVERY_X_HOURS) {
                    "${stringResource(R.string.starting_from)} ${DateUtils.formatTime(schedule.hour, schedule.minute)}"
                } else {
                    DateUtils.formatTime(schedule.hour, schedule.minute)
                }
                Text(timeLabel, style = MaterialTheme.typography.titleMedium)
            }

            // Frequency
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ScheduleFrequency.entries.forEach { freq ->
                    FilterChip(
                        selected = schedule.frequency == freq,
                        onClick = { onFrequencyChange(freq) },
                        label = { Text(freq.displayName, style = MaterialTheme.typography.labelSmall) },
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

            // Interval selector (every X days)
            if (schedule.frequency == ScheduleFrequency.INTERVAL) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.every), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    NumericTextField(
                        value = schedule.intervalDays,
                        onValueCommit = onIntervalChange,
                        modifier = Modifier.width(72.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.days), style = MaterialTheme.typography.bodyMedium)
                }
            }

            // Every X hours selector
            if (schedule.frequency == ScheduleFrequency.EVERY_X_HOURS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.every), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    NumericTextField(
                        value = schedule.intervalHours,
                        onValueCommit = onIntervalHoursChange,
                        modifier = Modifier.width(72.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.hours), style = MaterialTheme.typography.bodyMedium)
                }

                // Tolerance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tolerance), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    NumericTextField(
                        value = schedule.toleranceMinutes,
                        onValueCommit = onToleranceChange,
                        modifier = Modifier.width(72.dp),
                        defaultValue = 10
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.minutes), style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Duration section
            Text(stringResource(R.string.duration), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DurationType.entries.forEach { type ->
                    FilterChip(
                        selected = schedule.durationType == type,
                        onClick = { onDurationTypeChange(type) },
                        label = { Text(type.displayName, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }

            // Duration value input
            if (schedule.durationType == DurationType.DAYS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.number_of_days), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    NumericTextField(
                        value = schedule.durationValue,
                        onValueCommit = onDurationValueChange,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }

            if (schedule.durationType == DurationType.MONTHS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.number_of_months), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.width(8.dp))
                    NumericTextField(
                        value = schedule.durationValue,
                        onValueCommit = onDurationValueChange,
                        modifier = Modifier.width(80.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NumericTextField(
    value: Int,
    onValueCommit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    defaultValue: Int = 1
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    var hasFocus by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = text,
        onValueChange = { newText ->
            if (newText.isEmpty() || newText.all { it.isDigit() }) {
                text = newText
                newText.toIntOrNull()?.let(onValueCommit)
            }
        },
        modifier = modifier.onFocusChanged { focusState ->
            if (hasFocus && !focusState.isFocused) {
                if (text.isEmpty() || text.toIntOrNull() == null) {
                    text = defaultValue.toString()
                    onValueCommit(defaultValue)
                }
            }
            hasFocus = focusState.isFocused
        },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}
