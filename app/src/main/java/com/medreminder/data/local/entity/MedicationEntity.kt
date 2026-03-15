package com.medreminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class MedicationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val dosage: String,
    val dosageUnit: String,
    val form: String, // pill, capsule, liquid, injection, patch, spray, drops, inhaler, cream, other
    val instructions: String = "",
    val color: String = "#4A90D9",
    val iconName: String = "pill",
    val currentStock: Int = 0,
    val refillThreshold: Int = 5,
    val refillReminder: Boolean = true,
    val notes: String = "",
    val notifyCaregivers: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
