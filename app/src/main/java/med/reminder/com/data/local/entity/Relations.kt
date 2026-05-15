package med.reminder.com.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

data class MedicationWithSchedules(
    @Embedded val medication: MedicationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val schedules: List<ScheduleEntity>
)

data class MedicationWithLogs(
    @Embedded val medication: MedicationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val doseLogs: List<DoseLogEntity>
)

data class ScheduleWithMedication(
    @Embedded val schedule: ScheduleEntity,
    @Relation(
        parentColumn = "medicationId",
        entityColumn = "id"
    )
    val medication: MedicationEntity
)
