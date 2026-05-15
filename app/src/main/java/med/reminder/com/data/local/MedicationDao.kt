package med.reminder.com.data.local

import androidx.room.*
import med.reminder.com.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedication(medication: MedicationEntity): Long

    @Update
    suspend fun updateMedication(medication: MedicationEntity)

    @Delete
    suspend fun deleteMedication(medication: MedicationEntity)

    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications ORDER BY name ASC")
    fun getAllMedications(): Flow<List<MedicationEntity>>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationById(id: Long): MedicationEntity?

    @Query("SELECT * FROM medications WHERE id IN (:ids)")
    suspend fun getMedicationsByIds(ids: List<Long>): List<MedicationEntity>

    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationByIdFlow(id: Long): Flow<MedicationEntity?>

    @Transaction
    @Query("SELECT * FROM medications WHERE isActive = 1 ORDER BY name ASC")
    fun getMedicationsWithSchedules(): Flow<List<MedicationWithSchedules>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun getMedicationWithSchedules(id: Long): MedicationWithSchedules?

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :id")
    fun getMedicationWithSchedulesFlow(id: Long): Flow<MedicationWithSchedules?>

    @Query("SELECT COUNT(*) FROM medications WHERE isActive = 1")
    fun getActiveMedicationCount(): Flow<Int>

    @Query("UPDATE medications SET currentStock = currentStock - 1 WHERE id = :id AND currentStock > 0")
    suspend fun decrementStock(id: Long)

    @Query("SELECT * FROM medications WHERE isActive = 1 AND refillReminder = 1 AND currentStock > 0 AND currentStock <= refillThreshold")
    fun getMedicationsNeedingRefill(): Flow<List<MedicationEntity>>

    @Query("UPDATE medications SET isActive = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun deactivateMedication(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM medications")
    suspend fun getAllMedicationsSync(): List<MedicationEntity>

    @Query("DELETE FROM medications")
    suspend fun deleteAllMedications()
}
