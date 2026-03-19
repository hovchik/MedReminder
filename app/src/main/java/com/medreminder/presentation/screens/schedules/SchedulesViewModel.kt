package com.medreminder.presentation.screens.schedules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.domain.model.Medication
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SchedulesUiState(
    val medications: List<Medication> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState: StateFlow<SchedulesUiState> = _uiState.asStateFlow()

    init {
        loadMedications()
    }

    private fun loadMedications() {
        viewModelScope.launch {
            repository.getMedicationsWithSchedules().collect { meds ->
                _uiState.update {
                    it.copy(
                        medications = meds.filter { m -> m.isActive },
                        isLoading = false
                    )
                }
            }
        }
    }

    fun toggleScheduleEnabled(medicationId: Long, scheduleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            val med = repository.getMedicationById(medicationId) ?: return@launch
            val updatedSchedules = med.schedules.map { s ->
                if (s.id == scheduleId) s.copy(isEnabled = enabled) else s
            }
            repository.updateMedication(med, updatedSchedules)
        }
    }
}
