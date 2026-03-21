package com.medreminder.domain.repository

import com.medreminder.domain.model.*
import kotlinx.coroutines.flow.Flow

interface MedicationRepository {
    // Medications
    fun getActiveMedications(): Flow<List<Medication>>
    fun getMedicationsWithSchedules(): Flow<List<Medication>>
    suspend fun getMedicationById(id: Long): Medication?
    fun getMedicationByIdFlow(id: Long): Flow<Medication?>
    suspend fun addMedication(medication: Medication, schedules: List<Schedule>): Long
    suspend fun updateMedication(medication: Medication, schedules: List<Schedule>)
    suspend fun deleteMedication(id: Long)
    suspend fun deactivateMedication(id: Long)
    fun getActiveMedicationCount(): Flow<Int>
    fun getMedicationsNeedingRefill(): Flow<List<Medication>>

    // Schedules
    suspend fun getAllActiveSchedules(): List<Schedule>
    suspend fun getSchedulesForMedication(medicationId: Long): List<Schedule>

    // Dose Logs
    suspend fun createDoseLog(log: DoseLog): Long
    suspend fun markDoseTaken(logId: Long)
    suspend fun markDoseSkipped(logId: Long)
    suspend fun snoozeDose(logId: Long, snoozedUntil: Long)
    suspend fun markOverdueDosesAsMissed(cutoffTime: Long)
    suspend fun deleteOrphanPendingLogs(startTime: Long, endTime: Long)
    fun getTodayDoses(startOfDay: Long, endOfDay: Long): Flow<List<DoseLog>>
    fun getDoseLogsForDateRange(startTime: Long, endTime: Long): Flow<List<DoseLog>>
    suspend fun getDoseLogById(id: Long): DoseLog?
    suspend fun doseLogExistsForWindow(scheduleId: Long, windowStart: Long, windowEnd: Long): Boolean

    // Adherence
    suspend fun getAdherenceStats(startTime: Long, endTime: Long): AdherenceStats
    suspend fun getCurrentStreak(): Int

    // Caregivers
    fun getActiveCaregivers(): Flow<List<Caregiver>>
    suspend fun addCaregiver(caregiver: Caregiver): Long
    suspend fun updateCaregiver(caregiver: Caregiver)
    suspend fun deleteCaregiver(caregiver: Caregiver)
    suspend fun getCaregiversForMissedAlert(): List<Caregiver>

    // Family Members
    fun getActiveFamilyMembers(): Flow<List<FamilyMember>>
    suspend fun addFamilyMember(member: FamilyMember): Long
    suspend fun updateFamilyMember(member: FamilyMember)
    suspend fun deleteFamilyMember(member: FamilyMember)
    suspend fun getFamilyMemberById(id: Long): FamilyMember?

    // Data management
    suspend fun clearAllData()
    suspend fun exportAllData(): String
    suspend fun importAllData(json: String)
}
