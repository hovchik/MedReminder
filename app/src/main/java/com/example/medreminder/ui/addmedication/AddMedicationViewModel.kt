package com.example.medreminder.ui.addmedication

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medreminder.data.model.Medication
import com.example.medreminder.data.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddMedicationUiState(
    val name: String = "",
    val dosage: String = "",
    val frequencyHours: Int = 24,
    val reminderTimeHour: Int = 8,
    val reminderTimeMinute: Int = 0,
    val notes: String = "",
    val isEditing: Boolean = false,
    val isSaved: Boolean = false
)

@HiltViewModel
class AddMedicationViewModel @Inject constructor(
    private val repository: MedicationRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val medicationId: Long = savedStateHandle.get<Long>("medicationId") ?: -1L

    private val _uiState = MutableStateFlow(AddMedicationUiState())
    val uiState: StateFlow<AddMedicationUiState> = _uiState.asStateFlow()

    init {
        if (medicationId != -1L) {
            viewModelScope.launch {
                repository.getMedicationById(medicationId)?.let { med ->
                    _uiState.value = AddMedicationUiState(
                        name = med.name,
                        dosage = med.dosage,
                        frequencyHours = med.frequencyHours,
                        reminderTimeHour = med.reminderTimeHour,
                        reminderTimeMinute = med.reminderTimeMinute,
                        notes = med.notes,
                        isEditing = true
                    )
                }
            }
        }
    }

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDosage(dosage: String) {
        _uiState.value = _uiState.value.copy(dosage = dosage)
    }

    fun updateFrequencyHours(hours: Int) {
        _uiState.value = _uiState.value.copy(frequencyHours = hours)
    }

    fun updateReminderTime(hour: Int, minute: Int) {
        _uiState.value = _uiState.value.copy(reminderTimeHour = hour, reminderTimeMinute = minute)
    }

    fun updateNotes(notes: String) {
        _uiState.value = _uiState.value.copy(notes = notes)
    }

    fun saveMedication() {
        val state = _uiState.value
        if (state.name.isBlank() || state.dosage.isBlank()) return

        viewModelScope.launch {
            val medication = Medication(
                id = if (state.isEditing) medicationId else 0,
                name = state.name.trim(),
                dosage = state.dosage.trim(),
                frequencyHours = state.frequencyHours,
                reminderTimeHour = state.reminderTimeHour,
                reminderTimeMinute = state.reminderTimeMinute,
                notes = state.notes.trim()
            )
            repository.insertMedication(medication)
            _uiState.value = _uiState.value.copy(isSaved = true)
        }
    }
}
