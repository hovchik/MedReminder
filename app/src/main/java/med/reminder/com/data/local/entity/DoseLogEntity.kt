package med.reminder.com.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_logs",
    foreignKeys = [
        ForeignKey(
            entity = MedicationEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("medicationId"), Index("scheduledTime"), Index("status")]
)
data class DoseLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val scheduleId: Long,
    val scheduledTime: Long,
    val actionTime: Long? = null,
    val status: String, // pending, taken, skipped, snoozed, missed
    val snoozedUntil: Long? = null,
    val snoozeCount: Int = 0,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
