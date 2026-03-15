package com.medreminder.data.local

import androidx.room.*
import com.medreminder.data.local.entity.CaregiverEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaregiverDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCaregiver(caregiver: CaregiverEntity): Long

    @Update
    suspend fun updateCaregiver(caregiver: CaregiverEntity)

    @Delete
    suspend fun deleteCaregiver(caregiver: CaregiverEntity)

    @Query("SELECT * FROM caregivers WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveCaregivers(): Flow<List<CaregiverEntity>>

    @Query("SELECT * FROM caregivers WHERE isActive = 1 AND notifyOnMissed = 1")
    suspend fun getCaregiversForMissedAlert(): List<CaregiverEntity>

    @Query("SELECT * FROM caregivers WHERE id = :id")
    suspend fun getCaregiverById(id: Long): CaregiverEntity?

    @Query("SELECT COUNT(*) FROM caregivers WHERE isActive = 1")
    fun getActiveCaregiverCount(): Flow<Int>
}
