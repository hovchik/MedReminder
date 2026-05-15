package med.reminder.com

import med.reminder.com.domain.model.*
import med.reminder.com.util.DrugInteractionChecker
import med.reminder.com.util.InteractionSeverity
import org.junit.Assert.*
import org.junit.Test

class DrugInteractionCheckerTest {

    @Test
    fun `warfarin and aspirin should trigger major interaction`() {
        val meds = listOf(
            Medication(id = 1, name = "Warfarin", dosage = "5", dosageUnit = "mg"),
            Medication(id = 2, name = "Aspirin", dosage = "81", dosageUnit = "mg")
        )
        val interactions = DrugInteractionChecker.checkInteractions(meds)
        assertTrue(interactions.isNotEmpty())
        assertEquals(InteractionSeverity.MAJOR, interactions.first().severity)
    }

    @Test
    fun `levothyroxine and calcium should trigger moderate interaction`() {
        val meds = listOf(
            Medication(id = 1, name = "Levothyroxine", dosage = "50", dosageUnit = "mcg"),
            Medication(id = 2, name = "Calcium", dosage = "600", dosageUnit = "mg")
        )
        val interactions = DrugInteractionChecker.checkInteractions(meds)
        assertTrue(interactions.isNotEmpty())
        assertEquals(InteractionSeverity.MODERATE, interactions.first().severity)
    }

    @Test
    fun `unrelated medications should have no interactions`() {
        val meds = listOf(
            Medication(id = 1, name = "Vitamin D", dosage = "1000", dosageUnit = "IU"),
            Medication(id = 2, name = "Melatonin", dosage = "3", dosageUnit = "mg")
        )
        val interactions = DrugInteractionChecker.checkInteractions(meds)
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun `SSRI group matching should work for fluoxetine`() {
        val meds = listOf(
            Medication(id = 1, name = "Fluoxetine", dosage = "20", dosageUnit = "mg"),
            Medication(id = 2, name = "Tramadol", dosage = "50", dosageUnit = "mg")
        )
        val interactions = DrugInteractionChecker.checkInteractions(meds)
        assertTrue(interactions.isNotEmpty())
        assertEquals(InteractionSeverity.MAJOR, interactions.first().severity)
    }

    @Test
    fun `single medication should have no interactions`() {
        val meds = listOf(
            Medication(id = 1, name = "Warfarin", dosage = "5", dosageUnit = "mg")
        )
        val interactions = DrugInteractionChecker.checkInteractions(meds)
        assertTrue(interactions.isEmpty())
    }

    @Test
    fun `empty list should have no interactions`() {
        val interactions = DrugInteractionChecker.checkInteractions(emptyList())
        assertTrue(interactions.isEmpty())
    }
}

class ModelMappingTest {

    @Test
    fun `MedicationForm fromString should handle valid input`() {
        assertEquals(MedicationForm.PILL, MedicationForm.fromString("pill"))
        assertEquals(MedicationForm.CAPSULE, MedicationForm.fromString("CAPSULE"))
        assertEquals(MedicationForm.LIQUID, MedicationForm.fromString("Liquid"))
    }

    @Test
    fun `MedicationForm fromString should default to PILL for unknown input`() {
        assertEquals(MedicationForm.PILL, MedicationForm.fromString("unknown"))
        assertEquals(MedicationForm.PILL, MedicationForm.fromString(""))
    }

    @Test
    fun `DoseStatus fromString should handle all statuses`() {
        assertEquals(DoseStatus.TAKEN, DoseStatus.fromString("taken"))
        assertEquals(DoseStatus.MISSED, DoseStatus.fromString("MISSED"))
        assertEquals(DoseStatus.PENDING, DoseStatus.fromString("pending"))
        assertEquals(DoseStatus.SNOOZED, DoseStatus.fromString("snoozed"))
        assertEquals(DoseStatus.SKIPPED, DoseStatus.fromString("skipped"))
    }

    @Test
    fun `Schedule timeFormatted should format correctly`() {
        val morningSchedule = Schedule(timeHour = 8, timeMinute = 30)
        assertEquals("8:30 AM", morningSchedule.timeFormatted)

        val eveningSchedule = Schedule(timeHour = 20, timeMinute = 0)
        assertEquals("8:00 PM", eveningSchedule.timeFormatted)

        val noonSchedule = Schedule(timeHour = 12, timeMinute = 0)
        assertEquals("12:00 PM", noonSchedule.timeFormatted)

        val midnightSchedule = Schedule(timeHour = 0, timeMinute = 15)
        assertEquals("12:15 AM", midnightSchedule.timeFormatted)
    }

    @Test
    fun `Medication entity mapping round-trip preserves data`() {
        val medication = Medication(
            id = 42, name = "Aspirin", dosage = "81", dosageUnit = "mg",
            form = MedicationForm.PILL, instructions = "With food",
            color = "#FF0000", currentStock = 30, refillThreshold = 10,
            refillReminder = true, notes = "Baby aspirin"
        )
        val entity = medication.toEntity()
        val restored = entity.toDomain()

        assertEquals(medication.name, restored.name)
        assertEquals(medication.dosage, restored.dosage)
        assertEquals(medication.dosageUnit, restored.dosageUnit)
        assertEquals(medication.form, restored.form)
        assertEquals(medication.instructions, restored.instructions)
        assertEquals(medication.currentStock, restored.currentStock)
        assertEquals(medication.refillThreshold, restored.refillThreshold)
    }

    @Test
    fun `Schedule entity mapping round-trip preserves data`() {
        val schedule = Schedule(
            id = 10, medicationId = 42, timeHour = 9, timeMinute = 0,
            frequency = ScheduleFrequency.SPECIFIC_DAYS,
            daysOfWeek = listOf(2, 3, 4, 5, 6), // Mon-Fri
            intervalDays = 1
        )
        val entity = schedule.toEntity()
        val restored = entity.toDomain()

        assertEquals(schedule.timeHour, restored.timeHour)
        assertEquals(schedule.timeMinute, restored.timeMinute)
        assertEquals(schedule.frequency, restored.frequency)
        assertEquals(schedule.daysOfWeek, restored.daysOfWeek)
    }
}
