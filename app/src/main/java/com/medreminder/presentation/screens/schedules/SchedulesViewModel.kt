package com.medreminder.presentation.screens.schedules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.domain.model.Medication
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserMedicationGroup(
    val userId: Long?,          // null = self
    val userName: String,
    val medications: List<Medication>
)

data class SchedulesUiState(
    val userGroups: List<UserMedicationGroup> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val repository: MedicationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState: StateFlow<SchedulesUiState> = _uiState.asStateFlow()

    init {
        loadMedications()
    }

    private fun loadMedications() {
        viewModelScope.launch {
            repository.getMedicationsWithSchedules().collect { meds ->
                val active = meds.filter { it.isActive }
                val grouped = active.groupBy { it.assignedToId }
                val selfLabel = context.getString(R.string.my_medications_label)

                val groups = grouped.map { (userId, userMeds) ->
                    UserMedicationGroup(
                        userId = userId,
                        userName = if (userId == null) selfLabel else userMeds.first().assignedToName,
                        medications = userMeds
                    )
                }.sortedBy { if (it.userId == null) 0 else 1 }

                _uiState.update {
                    it.copy(userGroups = groups, isLoading = false)
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
