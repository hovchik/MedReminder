package com.medreminder.di

import android.content.Context
import androidx.room.Room
import com.medreminder.ai.modelmanager.LocalAiModelDao
import com.medreminder.data.local.*
import com.medreminder.data.repository.MedicationRepositoryImpl
import com.medreminder.domain.repository.MedicationRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(
            context, AppDatabase::class.java, AppDatabase.DATABASE_NAME
        ).addMigrations(AppDatabase.MIGRATION_2_3, AppDatabase.MIGRATION_3_4)
            .fallbackToDestructiveMigration().build()

    @Provides
    fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideDoseLogDao(db: AppDatabase): DoseLogDao = db.doseLogDao()

    @Provides
    fun provideCaregiverDao(db: AppDatabase): CaregiverDao = db.caregiverDao()

    @Provides
    fun provideLocalAiModelDao(db: AppDatabase): LocalAiModelDao = db.localAiModelDao()

    @Provides
    @Singleton
    fun provideMedicationRepository(
        database: AppDatabase,
        medicationDao: MedicationDao,
        scheduleDao: ScheduleDao,
        doseLogDao: DoseLogDao,
        caregiverDao: CaregiverDao
    ): MedicationRepository = MedicationRepositoryImpl(database, medicationDao, scheduleDao, doseLogDao, caregiverDao)
}
