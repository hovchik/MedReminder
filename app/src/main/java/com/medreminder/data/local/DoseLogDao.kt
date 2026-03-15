package com.medreminder.data.local

import androidx.room.*
import com.medreminder.data.local.entity.DoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDoseLog(log: DoseLogEntity): Long

    @Update
    suspend fun updateDoseLog(log: DoseLogEntity)

    @Query("SELECT * FROM dose_logs WHERE medicationId = :medicationId ORDER BY scheduledTime DESC")
    fun getLogsForMedication(medicationId: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    fun getLogsForDateRange(startTime: Long, endTime: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    suspend fun getLogsForDateRangeSync(startTime: Long, endTime: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE status = 'pending' AND scheduledTime <= :currentTime ORDER BY scheduledTime ASC")
    suspend fun getPendingDoses(currentTime: Long): List<DoseLogEntity>

    @Query("SELECT * FROM dose_logs WHERE status = 'pending' AND scheduledTime BETWEEN :startTime AND :endTime ORDER BY scheduledTime ASC")
    fun getTodayPendingDoses(startTime: Long, endTime: Long): Flow<List<DoseLogEntity>>

    @Query("SELECT * FROM dose_logs WHERE id = :id")
    suspend fun getDoseLogById(id: Long): DoseLogEntity?

    @Query("UPDATE dose_logs SET status = :status, actionTime = :actionTime WHERE id = :id")
    suspend fun updateDoseStatus(id: Long, status: String, actionTime: Long = System.currentTimeMillis())

    @Query("UPDATE dose_logs SET status = 'snoozed', snoozedUntil = :snoozedUntil, snoozeCount = snoozeCount + 1 WHERE id = :id")
    suspend fun snoozeDose(id: Long, snoozedUntil: Long)

    // Adherence stats
    @Query("""
        SELECT COUNT(*) FROM dose_logs 
        WHERE status = 'taken' 
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getTakenCount(startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs 
        WHERE status IN ('taken', 'skipped', 'missed') 
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getTotalCompletedCount(startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs 
        WHERE status = 'missed' 
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getMissedCount(startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs 
        WHERE status = 'skipped' 
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getSkippedCount(startTime: Long, endTime: Long): Int

    // Streak calculation
    @Query("""
        SELECT DISTINCT DATE(scheduledTime / 1000, 'unixepoch', 'localtime') as day
        FROM dose_logs 
        WHERE status = 'missed' OR status = 'skipped'
        ORDER BY day DESC
        LIMIT 1
    """)
    suspend fun getLastMissedDay(): String?

    @Query("""
        SELECT COUNT(DISTINCT DATE(scheduledTime / 1000, 'unixepoch', 'localtime'))
        FROM dose_logs 
        WHERE status = 'taken'
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getAdherenceDaysCount(startTime: Long, endTime: Long): Int

    // Mark overdue pending doses as missed
    @Query("""
        UPDATE dose_logs SET status = 'missed' 
        WHERE status = 'pending' 
        AND scheduledTime < :cutoffTime
    """)
    suspend fun markOverdueDosesAsMissed(cutoffTime: Long)

    @Query("DELETE FROM dose_logs WHERE medicationId = :medicationId")
    suspend fun deleteLogsForMedication(medicationId: Long)

    // For generating pending doses check
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM dose_logs
            WHERE scheduleId = :scheduleId
            AND scheduledTime BETWEEN :windowStart AND :windowEnd
        )
    """)
    suspend fun doseLogExistsForWindow(scheduleId: Long, windowStart: Long, windowEnd: Long): Boolean

    @Query("SELECT * FROM dose_logs")
    suspend fun getAllDoseLogsSync(): List<DoseLogEntity>

    @Query("DELETE FROM dose_logs")
    suspend fun deleteAllDoseLogs()
}
