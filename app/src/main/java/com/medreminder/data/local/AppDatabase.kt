package com.medreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.medreminder.data.local.entity.*

@Database(
    entities = [
        MedicationEntity::class,
        ScheduleEntity::class,
        DoseLogEntity::class,
        CaregiverEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun caregiverDao(): CaregiverDao

    companion object {
        const val DATABASE_NAME = "medreminder_db"
    }
}
