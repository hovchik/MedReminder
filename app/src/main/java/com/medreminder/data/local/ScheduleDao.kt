package com.medreminder.data.local

import androidx.room.*
import com.medreminder.data.local.entity.ScheduleEntity
import com.medreminder.data.local.entity.ScheduleWithMedication
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>): List<Long>

    @Update
    suspend fun updateSchedule(schedule: ScheduleEntity)

    @Delete
    suspend fun deleteSchedule(schedule: ScheduleEntity)

    @Query("DELETE FROM schedules WHERE medicationId = :medicationId")
    suspend fun deleteSchedulesForMedication(medicationId: Long)

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId AND isEnabled = 1")
    fun getSchedulesForMedication(medicationId: Long): Flow<List<ScheduleEntity>>

    @Query("SELECT * FROM schedules WHERE medicationId = :medicationId AND isEnabled = 1")
    suspend fun getActiveSchedulesForMedication(medicationId: Long): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    suspend fun getAllActiveSchedules(): List<ScheduleEntity>

    @Transaction
    @Query("SELECT * FROM schedules WHERE isEnabled = 1")
    suspend fun getAllActiveSchedulesWithMedication(): List<ScheduleWithMedication>

    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): ScheduleEntity?

    @Transaction
    @Query("SELECT * FROM schedules WHERE id = :id")
    suspend fun getScheduleWithMedication(id: Long): ScheduleWithMedication?
}
