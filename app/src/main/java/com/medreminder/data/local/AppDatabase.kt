package com.medreminder.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.medreminder.ai.local.LocalAiModelEntity
import com.medreminder.ai.modelmanager.LocalAiModelDao
import com.medreminder.data.local.entity.*

@Database(
    entities = [
        MedicationEntity::class,
        ScheduleEntity::class,
        DoseLogEntity::class,
        CaregiverEntity::class,
        LocalAiModelEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun caregiverDao(): CaregiverDao
    abstract fun localAiModelDao(): LocalAiModelDao

    companion object {
        const val DATABASE_NAME = "medreminder_db"

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_ai_models ADD COLUMN description TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_ai_models ADD COLUMN downloadUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_ai_models ADD COLUMN parameterCount TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE local_ai_models ADD COLUMN downloadedBytes INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN notifyCaregivers INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN isEmergency INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
