package com.medreminder.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caregivers")
data class CaregiverEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val phone: String = "",
    val email: String = "",
    val relationship: String = "", // family, friend, doctor, nurse, other
    val notifyOnMissed: Boolean = true,
    val notifyOnTaken: Boolean = false,
    val notifyOnCancelled: Boolean = false,
    val notifyDelay: Int = 30, // minutes after missed dose to notify
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
