package com.example.medreminder.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val frequencyHours: Int = 24,
    val reminderTimeHour: Int = 8,
    val reminderTimeMinute: Int = 0,
    val isActive: Boolean = true,
    val notes: String = ""
)
