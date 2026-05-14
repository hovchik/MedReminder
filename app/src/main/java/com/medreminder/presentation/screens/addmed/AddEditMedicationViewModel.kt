package com.medreminder.presentation.screens.addmed

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.medreminder.R
import com.medreminder.ai.AiLanguageHelper
import com.medreminder.ai.MedicationInfoSection
import com.medreminder.ai.local.AiProviderSelector
import com.medreminder.alarm.AlarmScheduler
import com.medreminder.billing.SubscriptionRepository
import com.medreminder.domain.model.*
import com.medreminder.domain.repository.MedicationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
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
    val error: String? = null,
    // AI suggestion state
    val isAiSuggesting: Boolean = false,
    val aiMedInfo: MedicationInfoSection? = null,
    val aiSuggestionError: String? = null
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
    private val providerSelector: AiProviderSelector,
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

    fun dismissAiInfo() {
        _uiState.update { it.copy(aiMedInfo = null, aiSuggestionError = null) }
    }

    fun requestAiSuggestion() {
        val name = _uiState.value.name.trim()
        if (name.isBlank()) {
            _uiState.update { it.copy(aiSuggestionError = context.getString(R.string.ai_enter_name_first)) }
            return
        }

        _uiState.update { it.copy(isAiSuggesting = true, aiSuggestionError = null, aiMedInfo = null) }

        viewModelScope.launch {
            try {
                val provider = providerSelector.selectProvider()
                val prompt = buildAiSuggestionPrompt(name)
                val rawJson: String? = provider.generateRawCompletion(prompt)

                if (rawJson != null) {
                    val result = parseAiSuggestion(rawJson as String)
                    if (result != null) {
                        // Apply suggested fields to the form
                        applySuggestion(result)
                        _uiState.update { it.copy(isAiSuggesting = false, aiMedInfo = result.medInfo) }
                    } else {
                        _uiState.update {
                            it.copy(isAiSuggesting = false, aiSuggestionError = context.getString(R.string.ai_suggestion_failed))
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(isAiSuggesting = false, aiSuggestionError = context.getString(R.string.ai_suggestion_failed))
                    }
                }
            } catch (e: Exception) {
                Log.e("AddEditMedVM", "AI suggestion failed", e)
                _uiState.update {
                    it.copy(isAiSuggesting = false, aiSuggestionError = context.getString(R.string.ai_suggestion_failed))
                }
            }
        }
    }

    private fun buildAiSuggestionPrompt(medicationName: String): String {
        return """
            |You are a pharmaceutical assistant inside a medication reminder app.
            |The user is adding a new medication named "$medicationName".
            |
            |Suggest the most common/default values for this medication and provide brief medical information.
            |
            |Respond ONLY with valid JSON (no markdown, no extra text):
            |{
            |  "suggestion": {
            |    "dosage": "Most common dosage amount as a number string (e.g., '500', '10', '50')",
            |    "dosageUnit": "Most common unit: one of mg, g, ml, mcg, IU, units, drops, puffs, tablets",
            |    "form": "One of: PILL, CAPSULE, LIQUID, INJECTION, PATCH, SPRAY, DROPS, INHALER, CREAM, OTHER",
            |    "instructions": "Brief standard instructions (e.g., 'Take with food', 'Take on empty stomach 30 min before breakfast')",
            |    "frequencyTimesPerDay": 1
            |  },
            |  "medInfo": {
            |    "description": "Brief 2-3 sentence description of what this medication is and does",
            |    "drugClass": "Pharmacological class (e.g., 'NSAID', 'Beta-blocker', 'SSRI Antidepressant')",
            |    "genericName": "Generic / INN name of the active ingredient",
            |    "commonUses": ["Primary use", "Secondary use"],
            |    "sideEffects": ["Common side effect 1", "Common side effect 2", "Common side effect 3"],
            |    "importantWarnings": ["Key warning 1", "Key warning 2"]
            |  }
            |}${AiLanguageHelper.getLanguageInstruction()}
        """.trimMargin()
    }

    private data class AiSuggestionResult(
        val dosage: String,
        val dosageUnit: String,
        val form: MedicationForm,
        val instructions: String,
        val frequencyTimesPerDay: Int,
        val medInfo: MedicationInfoSection
    )

    private fun parseAiSuggestion(rawJson: String): AiSuggestionResult? {
        return try {
            val repaired = sanitizeAndRepairJson(rawJson)
            val json = JSONObject(repaired)

            val suggestion = json.optJSONObject("suggestion") ?: return null
            val medInfoJson = json.optJSONObject("medInfo")

            val dosage = suggestion.optString("dosage", "")
            val dosageUnit = suggestion.optString("dosageUnit", "mg")
            val formStr = suggestion.optString("form", "PILL")
            val form = try { MedicationForm.valueOf(formStr.uppercase()) } catch (_: Exception) { MedicationForm.PILL }
            val instructions = suggestion.optString("instructions", "")
            val freqPerDay = suggestion.optInt("frequencyTimesPerDay", 1)

            val medInfo = if (medInfoJson != null) {
                MedicationInfoSection(
                    description = medInfoJson.optString("description", ""),
                    drugClass = medInfoJson.optString("drugClass", ""),
                    genericName = medInfoJson.optString("genericName", ""),
                    commonUses = jsonArrayToList(medInfoJson.optJSONArray("commonUses")),
                    sideEffects = jsonArrayToList(medInfoJson.optJSONArray("sideEffects")),
                    importantWarnings = jsonArrayToList(medInfoJson.optJSONArray("importantWarnings"))
                )
            } else MedicationInfoSection()

            AiSuggestionResult(dosage, dosageUnit, form, instructions, freqPerDay, medInfo)
        } catch (e: Exception) {
            Log.e("AddEditMedVM", "Failed to parse AI suggestion JSON", e)
            null
        }
    }

    private fun applySuggestion(result: AiSuggestionResult) {
        _uiState.update { state ->
            state.copy(
                dosage = if (state.dosage.isBlank()) result.dosage else state.dosage,
                dosageUnit = result.dosageUnit.lowercase().let { unit ->
                    if (unit in listOf("mg", "g", "ml", "mcg", "iu", "units", "drops", "puffs", "tablets")) unit else state.dosageUnit
                },
                form = result.form,
                instructions = if (state.instructions.isBlank()) result.instructions else state.instructions
            )
        }
    }

    private fun sanitizeAndRepairJson(raw: String): String {
        val sb = StringBuilder(raw.length)
        var inString = false
        var escaped = false
        for (ch in raw) {
            when {
                escaped -> { sb.append(ch); escaped = false }
                ch == '\\' && inString -> { sb.append(ch); escaped = true }
                ch == '"' -> { inString = !inString; sb.append(ch) }
                inString && ch == '\n' -> sb.append("\\n")
                inString && ch == '\r' -> sb.append("\\r")
                inString && ch == '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        var sanitized = sb.toString().trimEnd()
        if (sanitized.endsWith("\\")) sanitized = sanitized.dropLast(1)

        var inStr = false; var esc = false
        val stack = ArrayDeque<Char>()
        for (ch in sanitized) {
            when {
                esc -> esc = false
                ch == '\\' && inStr -> esc = true
                ch == '"' -> inStr = !inStr
                !inStr && ch == '{' -> stack.addLast('{')
                !inStr && ch == '[' -> stack.addLast('[')
                !inStr && ch == '}' -> { if (stack.isNotEmpty() && stack.last() == '{') stack.removeLast() }
                !inStr && ch == ']' -> { if (stack.isNotEmpty() && stack.last() == '[') stack.removeLast() }
            }
        }
        val repair = StringBuilder(sanitized)
        if (inStr) repair.append('"')
        val trimmed = repair.toString().trimEnd()
        if (trimmed.endsWith(",") || trimmed.endsWith(":")) {
            repair.clear(); repair.append(trimmed.dropLast(1))
        }
        while (stack.isNotEmpty()) {
            when (stack.removeLast()) { '[' -> repair.append(']'); '{' -> repair.append('}') }
        }
        return repair.toString()
    }

    private fun jsonArrayToList(array: org.json.JSONArray?): List<String> {
        if (array == null) return emptyList()
        return (0 until array.length()).map { array.getString(it) }
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
