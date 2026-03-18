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

    /**
     * Deletes only PENDING dose logs for a medication within a time window.
     * Called when a medication's schedules are updated so stale pending entries
     * (referencing old schedule IDs) are removed and regenerated fresh.
     */
    @Query("""
        DELETE FROM dose_logs
        WHERE medicationId = :medicationId
        AND status = 'pending'
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun deletePendingLogsForMedication(medicationId: Long, startTime: Long, endTime: Long)

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

    @Query("""
        SELECT * FROM dose_logs
        WHERE status IN ('pending', 'snoozed')
        AND scheduledTime BETWEEN :windowStart AND :windowEnd
        ORDER BY scheduledTime ASC
    """)
    suspend fun getPendingDoseLogsInWindow(windowStart: Long, windowEnd: Long): List<DoseLogEntity>

    @Query("""
        SELECT * FROM dose_logs
        WHERE scheduleId = :scheduleId
        AND scheduledTime BETWEEN :windowStart AND :windowEnd
        AND status IN ('pending', 'snoozed')
        LIMIT 1
    """)
    suspend fun findActiveDoseLogForSchedule(scheduleId: Long, windowStart: Long, windowEnd: Long): DoseLogEntity?

    // Per-medication adherence stats
    @Query("""
        SELECT COUNT(*) FROM dose_logs
        WHERE medicationId = :medicationId AND status = 'taken'
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getTakenCountForMedication(medicationId: Long, startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs
        WHERE medicationId = :medicationId AND status = 'missed'
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getMissedCountForMedication(medicationId: Long, startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs
        WHERE medicationId = :medicationId AND status = 'skipped'
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getSkippedCountForMedication(medicationId: Long, startTime: Long, endTime: Long): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs
        WHERE medicationId = :medicationId AND status IN ('taken', 'skipped', 'missed')
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getTotalCountForMedication(medicationId: Long, startTime: Long, endTime: Long): Int

    @Query("""
        SELECT SUM(snoozeCount) FROM dose_logs
        WHERE medicationId = :medicationId
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getSnoozedCountForMedication(medicationId: Long, startTime: Long, endTime: Long): Int?

    // Global snooze total
    @Query("""
        SELECT SUM(snoozeCount) FROM dose_logs
        WHERE scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getTotalSnoozedCount(startTime: Long, endTime: Long): Int?

    // Average delay: actionTime - scheduledTime for taken doses (in milliseconds)
    @Query("""
        SELECT AVG(actionTime - scheduledTime) FROM dose_logs
        WHERE status = 'taken' AND actionTime IS NOT NULL
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getAverageDelayMs(startTime: Long, endTime: Long): Long?

    @Query("""
        SELECT AVG(actionTime - scheduledTime) FROM dose_logs
        WHERE medicationId = :medicationId AND status = 'taken' AND actionTime IS NOT NULL
        AND scheduledTime BETWEEN :startTime AND :endTime
    """)
    suspend fun getAverageDelayMsForMedication(medicationId: Long, startTime: Long, endTime: Long): Long?

    // Time-of-day adherence: taken count by hour ranges
    @Query("""
        SELECT COUNT(*) FROM dose_logs
        WHERE status = 'taken'
        AND scheduledTime BETWEEN :startTime AND :endTime
        AND CAST((scheduledTime / 1000 % 86400) / 3600 AS INTEGER) BETWEEN :hourStart AND :hourEnd
    """)
    suspend fun getTakenCountByHourRange(startTime: Long, endTime: Long, hourStart: Int, hourEnd: Int): Int

    @Query("""
        SELECT COUNT(*) FROM dose_logs
        WHERE status IN ('taken', 'skipped', 'missed')
        AND scheduledTime BETWEEN :startTime AND :endTime
        AND CAST((scheduledTime / 1000 % 86400) / 3600 AS INTEGER) BETWEEN :hourStart AND :hourEnd
    """)
    suspend fun getTotalCountByHourRange(startTime: Long, endTime: Long, hourStart: Int, hourEnd: Int): Int
}
