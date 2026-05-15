package med.reminder.com.domain.model

import med.reminder.com.data.local.entity.*

data class Medication(
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val dosageUnit: String,
    val form: MedicationForm = MedicationForm.PILL,
    val instructions: String = "",
    val color: String = "#4A90D9",
    val iconName: String = "pill",
    val currentStock: Int = 0,
    val refillThreshold: Int = 5,
    val refillReminder: Boolean = true,
    val notes: String = "",
    val notifyCaregivers: Boolean = false,
    val isEmergency: Boolean = false,
    val isActive: Boolean = true,
    val assignedToId: Long? = null, // null = self, otherwise family member id
    val assignedToName: String = "", // denormalized for display
    val schedules: List<Schedule> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

enum class MedicationForm(val displayName: String, val icon: String) {
    PILL("Pill", "pill"),
    CAPSULE("Capsule", "capsule"),
    LIQUID("Liquid", "liquid"),
    INJECTION("Injection", "injection"),
    PATCH("Patch", "patch"),
    SPRAY("Spray", "spray"),
    DROPS("Drops", "drops"),
    INHALER("Inhaler", "inhaler"),
    CREAM("Cream", "cream"),
    OTHER("Other", "other");

    companion object {
        fun fromString(value: String): MedicationForm =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: PILL
    }
}

data class Schedule(
    val id: Long = 0,
    val medicationId: Long = 0,
    val timeHour: Int,
    val timeMinute: Int,
    val frequency: ScheduleFrequency = ScheduleFrequency.DAILY,
    val daysOfWeek: List<Int> = emptyList(),
    val intervalDays: Int = 1,
    val intervalHours: Int = 0, // for EVERY_X_HOURS frequency
    val toleranceMinutes: Int = 10, // allowed error margin in minutes
    val durationType: DurationType = DurationType.ONGOING,
    val durationValue: Int = 0, // number of days/months depending on durationType
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val isEnabled: Boolean = true
) {
    val timeFormatted: String
        get() {
            val h = if (timeHour == 0) 12 else if (timeHour > 12) timeHour - 12 else timeHour
            val amPm = if (timeHour < 12) "AM" else "PM"
            return String.format(java.util.Locale.getDefault(), "%d:%02d %s", h, timeMinute, amPm)
        }
}

enum class ScheduleFrequency(val displayName: String) {
    DAILY("Every day"),
    SPECIFIC_DAYS("Specific days"),
    INTERVAL("Every X days"),
    EVERY_X_HOURS("Every X hours"),
    AS_NEEDED("As needed");

    companion object {
        fun fromString(value: String): ScheduleFrequency =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: DAILY
    }
}

enum class DurationType(val displayName: String) {
    ONGOING("Ongoing / Lifetime"),
    DAYS("For X days"),
    MONTHS("For X months");

    companion object {
        fun fromString(value: String): DurationType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: ONGOING
    }
}

data class DoseLog(
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long,
    val scheduledTime: Long,
    val actionTime: Long? = null,
    val status: DoseStatus = DoseStatus.PENDING,
    val snoozedUntil: Long? = null,
    val snoozeCount: Int = 0,
    val notes: String = "",
    val medicationName: String = "",
    val medicationDosage: String = "",
    val medicationColor: String = "#4A90D9",
    val assignedToId: Long? = null,
    val assignedToName: String = ""
)

enum class DoseStatus(val displayName: String) {
    PENDING("Pending"),
    TAKEN("Taken"),
    SKIPPED("Skipped"),
    SNOOZED("Snoozed"),
    MISSED("Missed");

    companion object {
        fun fromString(value: String): DoseStatus =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

data class AdherenceStats(
    val totalDoses: Int = 0,
    val takenDoses: Int = 0,
    val missedDoses: Int = 0,
    val skippedDoses: Int = 0,
    val adherenceRate: Float = 0f,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val weeklyData: List<DayAdherence> = emptyList()
)

data class DayAdherence(
    val dayName: String,
    val taken: Int,
    val total: Int,
    val rate: Float
)

data class FamilyMember(
    val id: Long = 0,
    val name: String,
    val age: Int,
    val relation: String = "",
    val isActive: Boolean = true
)

data class Caregiver(
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val relationship: String = "",
    val notifyOnMissed: Boolean = true,
    val notifyOnTaken: Boolean = false,
    val notifyOnCancelled: Boolean = false,
    val notifyDelay: Int = 30,
    val isActive: Boolean = true
)

// Mappers
fun MedicationEntity.toDomain(schedules: List<Schedule> = emptyList()) = Medication(
    id = id, name = name, dosage = dosage, dosageUnit = dosageUnit,
    form = MedicationForm.fromString(form), instructions = instructions,
    color = color, iconName = iconName, currentStock = currentStock,
    refillThreshold = refillThreshold, refillReminder = refillReminder,
    notes = notes, notifyCaregivers = notifyCaregivers, isEmergency = isEmergency,
    isActive = isActive, assignedToId = assignedToId, assignedToName = assignedToName,
    schedules = schedules, createdAt = createdAt
)

fun Medication.toEntity() = MedicationEntity(
    id = id, name = name, dosage = dosage, dosageUnit = dosageUnit,
    form = form.name.lowercase(), instructions = instructions, color = color,
    iconName = iconName, currentStock = currentStock,
    refillThreshold = refillThreshold, refillReminder = refillReminder,
    notes = notes, notifyCaregivers = notifyCaregivers, isEmergency = isEmergency,
    isActive = isActive, assignedToId = assignedToId, assignedToName = assignedToName,
    createdAt = createdAt
)

fun ScheduleEntity.toDomain() = Schedule(
    id = id, medicationId = medicationId, timeHour = timeHour,
    timeMinute = timeMinute, frequency = ScheduleFrequency.fromString(frequency),
    daysOfWeek = if (daysOfWeek.isBlank()) emptyList()
    else daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() },
    intervalDays = intervalDays, intervalHours = intervalHours,
    toleranceMinutes = toleranceMinutes,
    durationType = DurationType.fromString(durationType),
    durationValue = durationValue,
    startDate = startDate, endDate = endDate,
    isEnabled = isEnabled
)

fun Schedule.toEntity() = ScheduleEntity(
    id = id, medicationId = medicationId, timeHour = timeHour,
    timeMinute = timeMinute, frequency = frequency.name.lowercase(),
    daysOfWeek = daysOfWeek.joinToString(","),
    intervalDays = intervalDays, intervalHours = intervalHours,
    toleranceMinutes = toleranceMinutes,
    durationType = durationType.name.lowercase(),
    durationValue = durationValue,
    startDate = startDate, endDate = endDate,
    isEnabled = isEnabled
)

fun DoseLogEntity.toDomain(
    medicationName: String = "",
    medicationDosage: String = "",
    medicationColor: String = "#4A90D9",
    assignedToId: Long? = null,
    assignedToName: String = ""
) = DoseLog(
    id = id, medicationId = medicationId, scheduleId = scheduleId,
    scheduledTime = scheduledTime, actionTime = actionTime,
    status = DoseStatus.fromString(status), snoozedUntil = snoozedUntil,
    snoozeCount = snoozeCount, notes = notes,
    medicationName = medicationName, medicationDosage = medicationDosage,
    medicationColor = medicationColor, assignedToId = assignedToId,
    assignedToName = assignedToName
)

fun DoseLog.toEntity() = DoseLogEntity(
    id = id, medicationId = medicationId, scheduleId = scheduleId,
    scheduledTime = scheduledTime, actionTime = actionTime,
    status = status.name.lowercase(), snoozedUntil = snoozedUntil,
    snoozeCount = snoozeCount, notes = notes
)

fun CaregiverEntity.toDomain() = Caregiver(
    id = id, name = name, phone = phone, email = email,
    relationship = relationship, notifyOnMissed = notifyOnMissed,
    notifyOnTaken = notifyOnTaken, notifyOnCancelled = notifyOnCancelled,
    notifyDelay = notifyDelay, isActive = isActive
)

fun Caregiver.toEntity() = CaregiverEntity(
    id = id, name = name, phone = phone, email = email,
    relationship = relationship, notifyOnMissed = notifyOnMissed,
    notifyOnTaken = notifyOnTaken, notifyOnCancelled = notifyOnCancelled,
    notifyDelay = notifyDelay, isActive = isActive
)

fun FamilyMemberEntity.toDomain() = FamilyMember(
    id = id, name = name, age = age, relation = relation, isActive = isActive
)

fun FamilyMember.toEntity() = FamilyMemberEntity(
    id = id, name = name, age = age, relation = relation, isActive = isActive
)
