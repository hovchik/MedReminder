package com.medreminder.data.repository

import androidx.room.withTransaction
import com.medreminder.data.local.*
import com.medreminder.data.local.entity.MedicationEntity
import com.medreminder.domain.model.*
import com.medreminder.domain.repository.MedicationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val medicationDao: MedicationDao,
    private val scheduleDao: ScheduleDao,
    private val doseLogDao: DoseLogDao,
    private val caregiverDao: CaregiverDao
) : MedicationRepository {

    override fun getActiveMedications(): Flow<List<Medication>> =
        medicationDao.getActiveMedications().map { list ->
            list.map { it.toDomain() }
        }

    override fun getMedicationsWithSchedules(): Flow<List<Medication>> =
        medicationDao.getMedicationsWithSchedules().map { list ->
            list.map { mws ->
                mws.medication.toDomain(mws.schedules.map { it.toDomain() })
            }
        }

    override suspend fun getMedicationById(id: Long): Medication? {
        val medWithSchedules = medicationDao.getMedicationWithSchedules(id) ?: return null
        return medWithSchedules.medication.toDomain(
            medWithSchedules.schedules.map { it.toDomain() }
        )
    }

    override fun getMedicationByIdFlow(id: Long): Flow<Medication?> =
        medicationDao.getMedicationWithSchedulesFlow(id).map { mws ->
            mws?.medication?.toDomain(mws.schedules.map { it.toDomain() })
        }

    override suspend fun addMedication(medication: Medication, schedules: List<Schedule>): Long {
        val medId = medicationDao.insertMedication(medication.toEntity())
        val scheduleEntities = schedules.map { it.copy(medicationId = medId).toEntity() }
        scheduleDao.insertSchedules(scheduleEntities)
        return medId
    }

    override suspend fun updateMedication(medication: Medication, schedules: List<Schedule>) {
        medicationDao.updateMedication(
            medication.toEntity().copy(updatedAt = System.currentTimeMillis())
        )
        scheduleDao.deleteSchedulesForMedication(medication.id)
        val scheduleEntities = schedules.map {
            it.copy(id = 0, medicationId = medication.id).toEntity()
        }
        scheduleDao.insertSchedules(scheduleEntities)
    }

    override suspend fun deleteMedication(id: Long) {
        val med = medicationDao.getMedicationById(id) ?: return
        medicationDao.deleteMedication(med)
    }

    override suspend fun deactivateMedication(id: Long) {
        medicationDao.deactivateMedication(id)
    }

    override fun getActiveMedicationCount(): Flow<Int> =
        medicationDao.getActiveMedicationCount()

    override fun getMedicationsNeedingRefill(): Flow<List<Medication>> =
        medicationDao.getMedicationsNeedingRefill().map { list ->
            list.map { it.toDomain() }
        }

    // Schedules
    override suspend fun getAllActiveSchedules(): List<Schedule> =
        scheduleDao.getAllActiveSchedules().map { it.toDomain() }

    override suspend fun getSchedulesForMedication(medicationId: Long): List<Schedule> =
        scheduleDao.getActiveSchedulesForMedication(medicationId).map { it.toDomain() }

    // Dose Logs
    override suspend fun createDoseLog(log: DoseLog): Long =
        doseLogDao.insertDoseLog(log.toEntity())

    override suspend fun markDoseTaken(logId: Long) {
        val log = doseLogDao.getDoseLogById(logId) ?: return
        doseLogDao.updateDoseStatus(logId, "taken")
        medicationDao.decrementStock(log.medicationId)
    }

    override suspend fun markDoseSkipped(logId: Long) {
        doseLogDao.updateDoseStatus(logId, "skipped")
    }

    override suspend fun snoozeDose(logId: Long, snoozedUntil: Long) {
        doseLogDao.snoozeDose(logId, snoozedUntil)
    }

    override suspend fun markOverdueDosesAsMissed(cutoffTime: Long) {
        doseLogDao.markOverdueDosesAsMissed(cutoffTime)
    }

    override fun getTodayDoses(startOfDay: Long, endOfDay: Long): Flow<List<DoseLog>> =
        doseLogDao.getLogsForDateRange(startOfDay, endOfDay).map { list ->
            list.map { log ->
                val med = medicationDao.getMedicationById(log.medicationId)
                log.toDomain().copy(
                    medicationName = med?.name ?: "Unknown",
                    medicationDosage = "${med?.dosage ?: ""} ${med?.dosageUnit ?: ""}".trim(),
                    medicationColor = med?.color ?: "#4A90D9"
                )
            }
        }

    override fun getDoseLogsForDateRange(startTime: Long, endTime: Long): Flow<List<DoseLog>> =
        doseLogDao.getLogsForDateRange(startTime, endTime).map { list ->
            list.map { log ->
                val med = medicationDao.getMedicationById(log.medicationId)
                log.toDomain().copy(
                    medicationName = med?.name ?: "Unknown",
                    medicationDosage = "${med?.dosage ?: ""} ${med?.dosageUnit ?: ""}".trim(),
                    medicationColor = med?.color ?: "#4A90D9"
                )
            }
        }

    override suspend fun getDoseLogById(id: Long): DoseLog? =
        doseLogDao.getDoseLogById(id)?.toDomain()

    override suspend fun doseLogExistsForWindow(
        scheduleId: Long, windowStart: Long, windowEnd: Long
    ): Boolean = doseLogDao.doseLogExistsForWindow(scheduleId, windowStart, windowEnd)

    // Adherence
    override suspend fun getAdherenceStats(startTime: Long, endTime: Long): AdherenceStats {
        val taken = doseLogDao.getTakenCount(startTime, endTime)
        val total = doseLogDao.getTotalCompletedCount(startTime, endTime)
        val missed = doseLogDao.getMissedCount(startTime, endTime)
        val skipped = doseLogDao.getSkippedCount(startTime, endTime)
        val rate = if (total > 0) taken.toFloat() / total.toFloat() * 100f else 0f

        // Weekly data
        val calendar = Calendar.getInstance()
        val weeklyData = mutableListOf<DayAdherence>()
        val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

        calendar.timeInMillis = startTime
        while (calendar.timeInMillis < endTime) {
            val dayStart = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = calendar.timeInMillis

            val dayTaken = doseLogDao.getTakenCount(dayStart, dayEnd)
            val dayTotal = doseLogDao.getTotalCompletedCount(dayStart, dayEnd)
            val dayRate = if (dayTotal > 0) dayTaken.toFloat() / dayTotal * 100f else 0f

            val dayOfWeek = Calendar.getInstance().apply { timeInMillis = dayStart }
                .get(Calendar.DAY_OF_WEEK) - 1

            weeklyData.add(
                DayAdherence(
                    dayName = dayNames[dayOfWeek],
                    taken = dayTaken,
                    total = dayTotal,
                    rate = dayRate
                )
            )
        }

        return AdherenceStats(
            totalDoses = total,
            takenDoses = taken,
            missedDoses = missed,
            skippedDoses = skipped,
            adherenceRate = rate,
            currentStreak = getCurrentStreak(),
            weeklyData = weeklyData
        )
    }

    override suspend fun getCurrentStreak(): Int {
        val calendar = Calendar.getInstance()
        var streak = 0
        var checkDate = Calendar.getInstance()

        // Go back day by day checking if all doses were taken
        for (i in 0..365) {
            checkDate.set(Calendar.HOUR_OF_DAY, 0)
            checkDate.set(Calendar.MINUTE, 0)
            checkDate.set(Calendar.SECOND, 0)
            checkDate.set(Calendar.MILLISECOND, 0)
            val dayStart = checkDate.timeInMillis

            checkDate.add(Calendar.DAY_OF_YEAR, 1)
            val dayEnd = checkDate.timeInMillis
            checkDate.add(Calendar.DAY_OF_YEAR, -1)

            val dayTotal = doseLogDao.getTotalCompletedCount(dayStart, dayEnd)
            val dayTaken = doseLogDao.getTakenCount(dayStart, dayEnd)

            if (dayTotal == 0) {
                if (i == 0) {
                    checkDate.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }

            if (dayTaken == dayTotal) {
                streak++
            } else {
                if (i > 0) break
            }

            checkDate.add(Calendar.DAY_OF_YEAR, -1)
        }

        return streak
    }

    // Caregivers
    override fun getActiveCaregivers(): Flow<List<Caregiver>> =
        caregiverDao.getActiveCaregivers().map { list -> list.map { it.toDomain() } }

    override suspend fun addCaregiver(caregiver: Caregiver): Long =
        caregiverDao.insertCaregiver(caregiver.toEntity())

    override suspend fun updateCaregiver(caregiver: Caregiver) =
        caregiverDao.updateCaregiver(caregiver.toEntity())

    override suspend fun deleteCaregiver(caregiver: Caregiver) =
        caregiverDao.deleteCaregiver(caregiver.toEntity())

    override suspend fun getCaregiversForMissedAlert(): List<Caregiver> =
        caregiverDao.getCaregiversForMissedAlert().map { it.toDomain() }

    // Data management
    override suspend fun clearAllData() {
        doseLogDao.deleteAllDoseLogs()
        scheduleDao.deleteAllSchedules()
        medicationDao.deleteAllMedications()
        caregiverDao.deleteAllCaregivers()
    }

    override suspend fun exportAllData(): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // Medications
        val meds = JSONArray()
        for (med in medicationDao.getAllMedicationsSync()) {
            meds.put(JSONObject().apply {
                put("id", med.id)
                put("name", med.name)
                put("dosage", med.dosage)
                put("dosageUnit", med.dosageUnit)
                put("form", med.form)
                put("instructions", med.instructions)
                put("color", med.color)
                put("iconName", med.iconName)
                put("currentStock", med.currentStock)
                put("refillThreshold", med.refillThreshold)
                put("refillReminder", med.refillReminder)
                put("notes", med.notes)
                put("notifyCaregivers", med.notifyCaregivers)
                put("isActive", med.isActive)
                put("createdAt", med.createdAt)
                put("updatedAt", med.updatedAt)
            })
        }
        root.put("medications", meds)

        // Schedules
        val schedules = JSONArray()
        for (s in scheduleDao.getAllSchedulesSync()) {
            schedules.put(JSONObject().apply {
                put("id", s.id)
                put("medicationId", s.medicationId)
                put("timeHour", s.timeHour)
                put("timeMinute", s.timeMinute)
                put("frequency", s.frequency)
                put("daysOfWeek", s.daysOfWeek)
                put("intervalDays", s.intervalDays)
                put("startDate", s.startDate)
                put("endDate", if (s.endDate != null) s.endDate else JSONObject.NULL)
                put("isEnabled", s.isEnabled)
                put("createdAt", s.createdAt)
            })
        }
        root.put("schedules", schedules)

        // Dose logs
        val logs = JSONArray()
        for (log in doseLogDao.getAllDoseLogsSync()) {
            logs.put(JSONObject().apply {
                put("id", log.id)
                put("medicationId", log.medicationId)
                put("scheduleId", log.scheduleId)
                put("scheduledTime", log.scheduledTime)
                put("actionTime", if (log.actionTime != null) log.actionTime else JSONObject.NULL)
                put("status", log.status)
                put("snoozedUntil", if (log.snoozedUntil != null) log.snoozedUntil else JSONObject.NULL)
                put("snoozeCount", log.snoozeCount)
                put("notes", log.notes)
                put("createdAt", log.createdAt)
            })
        }
        root.put("doseLogs", logs)

        // Caregivers
        val caregivers = JSONArray()
        for (c in caregiverDao.getAllCaregiversSync()) {
            caregivers.put(JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("email", c.email)
                put("relationship", c.relationship)
                put("notifyOnMissed", c.notifyOnMissed)
                put("notifyOnTaken", c.notifyOnTaken)
                put("notifyDelay", c.notifyDelay)
                put("isActive", c.isActive)
                put("createdAt", c.createdAt)
            })
        }
        root.put("caregivers", caregivers)

        return root.toString(2)
    }

    override suspend fun importAllData(json: String) {
        val root = JSONObject(json)

        database.withTransaction {
            // Clear existing data first
            clearAllData()

            // Import medications
            val meds = root.optJSONArray("medications")
            if (meds != null) {
                for (i in 0 until meds.length()) {
                    val m = meds.getJSONObject(i)
                    medicationDao.insertMedication(MedicationEntity(
                        id = m.getLong("id"),
                        name = m.getString("name"),
                        dosage = m.getString("dosage"),
                        dosageUnit = m.getString("dosageUnit"),
                        form = m.getString("form"),
                        instructions = m.optString("instructions", ""),
                        color = m.optString("color", "#4A90D9"),
                        iconName = m.optString("iconName", "pill"),
                        currentStock = m.optInt("currentStock", 0),
                        refillThreshold = m.optInt("refillThreshold", 5),
                        refillReminder = m.optBoolean("refillReminder", true),
                        notes = m.optString("notes", ""),
                        notifyCaregivers = m.optBoolean("notifyCaregivers", false),
                        isActive = m.optBoolean("isActive", true),
                        createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = m.optLong("updatedAt", System.currentTimeMillis())
                    ))
                }
            }

            // Import schedules
            val schedules = root.optJSONArray("schedules")
            if (schedules != null) {
                for (i in 0 until schedules.length()) {
                    val s = schedules.getJSONObject(i)
                    scheduleDao.insertSchedule(com.medreminder.data.local.entity.ScheduleEntity(
                        id = s.getLong("id"),
                        medicationId = s.getLong("medicationId"),
                        timeHour = s.getInt("timeHour"),
                        timeMinute = s.getInt("timeMinute"),
                        frequency = s.getString("frequency"),
                        daysOfWeek = s.optString("daysOfWeek", ""),
                        intervalDays = s.optInt("intervalDays", 1),
                        startDate = s.optLong("startDate", System.currentTimeMillis()),
                        endDate = if (s.isNull("endDate")) null else s.optLong("endDate"),
                        isEnabled = s.optBoolean("isEnabled", true),
                        createdAt = s.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }

            // Import dose logs
            val logs = root.optJSONArray("doseLogs")
            if (logs != null) {
                for (i in 0 until logs.length()) {
                    val l = logs.getJSONObject(i)
                    doseLogDao.insertDoseLog(com.medreminder.data.local.entity.DoseLogEntity(
                        id = l.getLong("id"),
                        medicationId = l.getLong("medicationId"),
                        scheduleId = l.getLong("scheduleId"),
                        scheduledTime = l.getLong("scheduledTime"),
                        actionTime = if (l.isNull("actionTime")) null else l.optLong("actionTime"),
                        status = l.getString("status"),
                        snoozedUntil = if (l.isNull("snoozedUntil")) null else l.optLong("snoozedUntil"),
                        snoozeCount = l.optInt("snoozeCount", 0),
                        notes = l.optString("notes", ""),
                        createdAt = l.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }

            // Import caregivers
            val caregivers = root.optJSONArray("caregivers")
            if (caregivers != null) {
                for (i in 0 until caregivers.length()) {
                    val c = caregivers.getJSONObject(i)
                    caregiverDao.insertCaregiver(com.medreminder.data.local.entity.CaregiverEntity(
                        id = c.getLong("id"),
                        name = c.getString("name"),
                        phone = c.optString("phone", ""),
                        email = c.optString("email", ""),
                        relationship = c.optString("relationship", ""),
                        notifyOnMissed = c.optBoolean("notifyOnMissed", true),
                        notifyOnTaken = c.optBoolean("notifyOnTaken", false),
                        notifyDelay = c.optInt("notifyDelay", 30),
                        isActive = c.optBoolean("isActive", true),
                        createdAt = c.optLong("createdAt", System.currentTimeMillis())
                    ))
                }
            }
        }
    }
}
