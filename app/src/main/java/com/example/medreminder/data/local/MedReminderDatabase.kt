package com.example.medreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.medreminder.data.model.Medication

@Database(entities = [Medication::class], version = 1, exportSchema = false)
abstract class MedReminderDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
}
