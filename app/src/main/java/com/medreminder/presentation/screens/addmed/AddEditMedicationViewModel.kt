package com.medreminder.presentation.screens.addmed

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.alarm.AlarmScheduler
import com.medreminder.billing.SubscriptionRepository
import com.medreminder.domain.model.*
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditUiState(
    val name: String = "",
    val dosage: String = "",
    val dosageUnit: String = "mg",
    val form: MedicationForm = MedicationForm.PILL,
    val instructions: String = "",
    val color: String = "#4A90D9",
    val currentStock: String = "",
    val refillThreshold: String = "5",
    val refillReminder: Boolean = true,
    val notes: String = "",
    val notifyCaregivers: Boolean = false,
    val isEmergency: Boolean = false,
    val assignedToId: Long? = null,
    val assignedToName: String = "",
    val schedules: List<ScheduleInput> = listOf(ScheduleInput()),
    val familyMembers: List<FamilyMember> = emptyList(),
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val error: String? = null
)

data class ScheduleInput(
    val id: Long = 0,          // 0 = new schedule, non-zero = existing schedule being edited
    val hour: Int = 8,
    val minute: Int = 0,
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val daysOfWeek: List<Int> = emptyList(),
    val intervalDays: Int = 1,
    val intervalHours: Int = 8,
    val toleranceMinutes: Int = 10,
    val durationType: DurationType = DurationType.ONGOING,
    val durationValue: Int = 7
)

@HiltViewModel
class AddEditMedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val subscriptionRepository: SubscriptionRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var editingMedicationId: Long? = null

    init {
        viewModelScope.launch {
            repository.getActiveFamilyMembers().collect { members ->
                _uiState.update { it.copy(familyMembers = members) }
            }
        }
    }

    fun loadMedication(id: Long) {
        viewModelScope.launch {
            val med = repository.getMedicationById(id) ?: return@launch
            editingMedicationId = id
            _uiState.update {
                it.copy(
                    name = med.name,
                    dosage = med.dosage,
                    dosageUnit = med.dosageUnit,
                    form = med.form,
                    instructions = med.instructions,
                    color = med.color,
                    currentStock = if (med.currentStock > 0) med.currentStock.toString() else "",
                    refillThreshold = med.refillThreshold.toString(),
                    refillReminder = med.refillReminder,
                    notes = med.notes,
                    notifyCaregivers = med.notifyCaregivers,
                    isEmergency = med.isEmergency,
                    assignedToId = med.assignedToId,
                    assignedToName = med.assignedToName,
                    schedules = med.schedules.map { s ->
                        ScheduleInput(
                            id = s.id,           // preserve the schedule's DB id
                            hour = s.timeHour,
                            minute = s.timeMinute,
                            frequency = s.frequency,
                            daysOfWeek = s.daysOfWeek,
                            intervalDays = s.intervalDays,
                            intervalHours = if (s.intervalHours > 0) s.intervalHours else 8,
                            toleranceMinutes = s.toleranceMinutes,
                            durationType = s.durationType,
                            durationValue = if (s.durationValue > 0) s.durationValue else 7
                        )
                    }.ifEmpty { listOf(ScheduleInput()) },
                    isEditing = true
                )
            }
        }
    }

    fun updateName(name: String) { _uiState.update { it.copy(name = name, error = null) } }
    fun updateDosage(dosage: String) { _uiState.update { it.copy(dosage = dosage) } }
    fun updateDosageUnit(unit: String) { _uiState.update { it.copy(dosageUnit = unit) } }
    fun updateForm(form: MedicationForm) { _uiState.update { it.copy(form = form) } }
    fun updateInstructions(inst: String) { _uiState.update { it.copy(instructions = inst) } }
    fun updateColor(color: String) { _uiState.update { it.copy(color = color) } }
    fun updateStock(stock: String) { _uiState.update { it.copy(currentStock = stock) } }
    fun updateRefillThreshold(t: String) { _uiState.update { it.copy(refillThreshold = t) } }
    fun updateRefillReminder(r: Boolean) { _uiState.update { it.copy(refillReminder = r) } }
    fun updateNotes(notes: String) { _uiState.update { it.copy(notes = notes) } }
    fun updateNotifyCaregivers(notify: Boolean) { _uiState.update { it.copy(notifyCaregivers = notify) } }
    fun updateIsEmergency(emergency: Boolean) { _uiState.update { it.copy(isEmergency = emergency) } }

    fun updateAssignedTo(id: Long?, name: String) {
        _uiState.update { it.copy(assignedToId = id, assignedToName = name) }
    }

    fun addSchedule() {
        _uiState.update { it.copy(schedules = it.schedules + ScheduleInput()) }
    }

    fun removeSchedule(index: Int) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            if (list.size > 1) list.removeAt(index)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleTime(index: Int, hour: Int, minute: Int) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(hour = hour, minute = minute)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleFrequency(index: Int, freq: ScheduleFrequency) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(frequency = freq)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleDays(index: Int, days: List<Int>) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(daysOfWeek = days)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleInterval(index: Int, interval: Int) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(intervalDays = interval)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleIntervalHours(index: Int, hours: Int) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(intervalHours = hours)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleToleranceMinutes(index: Int, minutes: Int) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(toleranceMinutes = minutes)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleDurationType(index: Int, type: DurationType) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(durationType = type)
            it.copy(schedules = list)
        }
    }

    fun updateScheduleDurationValue(index: Int, value: Int) {
        _uiState.update {
            val list = it.schedules.toMutableList()
            list[index] = list[index].copy(durationValue = value)
            it.copy(schedules = list)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.medication_name_required)) }
            return
        }

        _uiState.update { it.copy(isSaving = true) }

        viewModelScope.launch {
            try {
                // Check medication limit for new medications (not edits)
                if (!state.isEditing) {
                    val limit = subscriptionRepository.getMedicationLimit().first()
                    if (limit != null) {
                        val currentCount = repository.getActiveMedicationCount().first()
                        if (currentCount >= limit) {
                            _uiState.update {
                                it.copy(
                                    isSaving = false,
                                    error = context.getString(R.string.medication_limit_reached, limit)
                                )
                            }
                            return@launch
                        }
                    }
                }

                val medication = Medication(
                    id = editingMedicationId ?: 0,
                    name = state.name.trim(),
                    dosage = state.dosage.trim(),
                    dosageUnit = state.dosageUnit,
                    form = state.form,
                    instructions = state.instructions.trim(),
                    color = state.color,
                    currentStock = state.currentStock.toIntOrNull() ?: 0,
                    refillThreshold = state.refillThreshold.toIntOrNull() ?: 5,
                    refillReminder = state.refillReminder,
                    notes = state.notes.trim(),
                    notifyCaregivers = state.notifyCaregivers,
                    isEmergency = state.isEmergency,
                    assignedToId = state.assignedToId,
                    assignedToName = state.assignedToName
                )

                val schedules = state.schedules.map {
                    Schedule(
                        id = it.id,              // 0 for new schedules, existing id for edits
                        timeHour = it.hour,
                        timeMinute = it.minute,
                        frequency = it.frequency,
                        daysOfWeek = it.daysOfWeek,
                        intervalDays = it.intervalDays,
                        intervalHours = it.intervalHours,
                        toleranceMinutes = it.toleranceMinutes,
                        durationType = it.durationType,
                        durationValue = it.durationValue
                    )
                }

                if (state.isEditing && editingMedicationId != null) {
                    repository.updateMedication(medication, schedules)
                } else {
                    repository.addMedication(medication, schedules)
                }

                alarmScheduler.scheduleAllAlarms()
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun deleteMedication() {
        editingMedicationId?.let { id ->
            viewModelScope.launch {
                repository.deleteMedication(id)
                alarmScheduler.scheduleAllAlarms()
                _uiState.update { it.copy(isSaved = true) }
            }
        }
    }
}
