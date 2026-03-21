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
        LocalAiModelEntity::class,
        FamilyMemberEntity::class
    ],
    version = 7,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun medicationDao(): MedicationDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun doseLogDao(): DoseLogDao
    abstract fun caregiverDao(): CaregiverDao
    abstract fun localAiModelDao(): LocalAiModelDao
    abstract fun familyMemberDao(): FamilyMemberDao

    companion object {
        const val DATABASE_NAME = "medreminder_db"

        /**
         * Returns a properly configured database instance with all migrations.
         * Use this in BroadcastReceivers and Activities that cannot use Hilt injection.
         */
        fun getInstance(context: android.content.Context): AppDatabase =
            androidx.room.Room.databaseBuilder(
                context.applicationContext, AppDatabase::class.java, DATABASE_NAME
            ).addMigrations(
                MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7
            ).fallbackToDestructiveMigration().build()

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

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caregivers ADD COLUMN notifyOnCancelled INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create family_members table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS family_members (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        age INTEGER NOT NULL,
                        relation TEXT NOT NULL DEFAULT '',
                        isActive INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())

                // Add assignedTo fields to medications
                db.execSQL("ALTER TABLE medications ADD COLUMN assignedToId INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE medications ADD COLUMN assignedToName TEXT NOT NULL DEFAULT ''")

                // Add new schedule fields
                db.execSQL("ALTER TABLE schedules ADD COLUMN intervalHours INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE schedules ADD COLUMN toleranceMinutes INTEGER NOT NULL DEFAULT 10")
                db.execSQL("ALTER TABLE schedules ADD COLUMN durationType TEXT NOT NULL DEFAULT 'ongoing'")
                db.execSQL("ALTER TABLE schedules ADD COLUMN durationValue INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
