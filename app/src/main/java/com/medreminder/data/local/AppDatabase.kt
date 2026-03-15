package com.medreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medreminder.ai.local.LocalAiModelEntity
import com.medreminder.ai.modelmanager.LocalAiModelDao
import com.medreminder.data.local.entity.*

@Database(
    entities = [
        MedicationEntity::class,
        ScheduleEntity::class,
        DoseLogEntity::class,
        CaregiverEntity::class,
        LocalAiModelEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun caregiverDao(): CaregiverDao
    abstract fun localAiModelDao(): LocalAiModelDao

    companion object {
        const val DATABASE_NAME = "medreminder_db"
    }
}
