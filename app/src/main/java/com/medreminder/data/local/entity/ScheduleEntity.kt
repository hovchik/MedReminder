package com.medreminder.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId")]
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val timeHour: Int,
    val timeMinute: Int,
    val frequency: String, // daily, specific_days, interval, every_x_hours, as_needed
    val daysOfWeek: String = "", // comma-separated: "1,2,3,4,5" for Mon-Fri
    val intervalDays: Int = 1, // for interval frequency
    val intervalHours: Int = 0, // for every_x_hours frequency
    val toleranceMinutes: Int = 10, // allowed error margin in minutes
    val durationType: String = "ongoing", // ongoing, days, months
    val durationValue: Int = 0, // number of days/months
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
