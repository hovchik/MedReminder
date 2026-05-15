package med.reminder.com.presentation.screens.schedules

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import med.reminder.com.R
import med.reminder.com.alarm.AlarmScheduler
import med.reminder.com.data.preferences.UserPreferencesManager
import med.reminder.com.domain.model.Medication
import med.reminder.com.domain.model.Schedule
import med.reminder.com.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserMedicationGroup(
    val userId: Long?,          // null = self
    val userName: String,
    val photoUri: String? = null,
    val medications: List<Medication>
)

data class SchedulesUiState(
    val userGroups: List<UserMedicationGroup> = emptyList(),
    val isLoading: Boolean = true
)

/**
 * Holds a snapshot of a medication that was soft-deleted (deactivated)
 * so it can be restored if the user taps Undo.
 */
data class PendingDelete(
    val medication: Medication,
    val schedules: List<Schedule>
)

@HiltViewModel
class SchedulesViewModel @Inject constructor(
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val userPreferencesManager: UserPreferencesManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(SchedulesUiState())
    val uiState: StateFlow<SchedulesUiState> = _uiState.asStateFlow()

    /** Emitted once per delete so the UI can show a Snackbar. */
    private val _deleteEvent = MutableSharedFlow<PendingDelete>()
    val deleteEvent: SharedFlow<PendingDelete> = _deleteEvent.asSharedFlow()

    private var pendingDelete: PendingDelete? = null
    private var confirmJob: Job? = null

    companion object {
        private const val UNDO_TIMEOUT_MS = 5_000L
    }

    init {
        loadMedications()
    }

    private fun loadMedications() {
        viewModelScope.launch {
            repository.getMedicationsWithSchedules().collect { meds ->
                val active = meds.filter { it.isActive }
                val grouped = active.groupBy { it.assignedToId }
                val selfLabel = context.getString(R.string.my_medications_label)
                val selfPhotoUri = userPreferencesManager.userPhotoUri.first()

                val groups = grouped.map { (userId, userMeds) ->
                    val photoUri = if (userId == null) {
                        selfPhotoUri
                    } else {
                        repository.getFamilyMemberById(userId)?.photoUri
                    }
                    UserMedicationGroup(
                        userId = userId,
                        userName = if (userId == null) selfLabel else userMeds.first().assignedToName,
                        photoUri = photoUri,
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

    /**
     * Removes a single schedule from a medication. If it was the last schedule,
     * the entire medication is deleted instead.
     */
    fun deleteSchedule(medicationId: Long, scheduleId: Long) {
        viewModelScope.launch {
            val med = repository.getMedicationById(medicationId) ?: return@launch
            val remaining = med.schedules.filter { it.id != scheduleId }
            if (remaining.isEmpty()) {
                repository.deleteMedication(medicationId)
            } else {
                repository.updateMedication(med, remaining)
            }
            alarmScheduler.scheduleAllAlarms()
        }
    }

    /**
     * Soft-deletes (deactivates) a medication.
     * A snapshot is kept so it can be restored if the user taps Undo within
     * [UNDO_TIMEOUT_MS]. After the timeout the medication is permanently deleted.
     */
    fun deleteMedication(medicationId: Long) {
        viewModelScope.launch {
            // If there's already a pending delete, finalize it immediately
            finalizePendingDelete()

            val med = repository.getMedicationById(medicationId) ?: return@launch
            val snapshot = PendingDelete(med, med.schedules)

            // Deactivate instead of deleting — hides from UI immediately
            repository.deactivateMedication(medicationId)
            alarmScheduler.scheduleAllAlarms()

            pendingDelete = snapshot
            _deleteEvent.emit(snapshot)

            // Auto-confirm after timeout
            confirmJob = viewModelScope.launch {
                delay(UNDO_TIMEOUT_MS)
                finalizePendingDelete()
            }
        }
    }

    /** Called when the user taps Undo on the snackbar. */
    fun undoDelete() {
        val snapshot = pendingDelete ?: return
        confirmJob?.cancel()
        confirmJob = null
        pendingDelete = null

        viewModelScope.launch {
            // Re-activate the medication with its original schedules
            repository.updateMedication(
                snapshot.medication.copy(isActive = true),
                snapshot.schedules
            )
            alarmScheduler.scheduleAllAlarms()
        }
    }

    /** Permanently deletes the pending medication. */
    private suspend fun finalizePendingDelete() {
        val snapshot = pendingDelete ?: return
        confirmJob?.cancel()
        confirmJob = null
        pendingDelete = null
        repository.deleteMedication(snapshot.medication.id)
        alarmScheduler.scheduleAllAlarms()
    }
}
