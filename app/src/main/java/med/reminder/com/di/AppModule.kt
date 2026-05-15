package med.reminder.com.di

import android.content.Context
import med.reminder.com.data.local.*
import med.reminder.com.data.repository.MedicationRepositoryImpl
import med.reminder.com.domain.repository.MedicationRepository
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
        AppDatabase.getInstance(context)

    @Provides
    fun provideMedicationDao(db: AppDatabase): MedicationDao = db.medicationDao()

    @Provides
    fun provideScheduleDao(db: AppDatabase): ScheduleDao = db.scheduleDao()

    @Provides
    fun provideDoseLogDao(db: AppDatabase): DoseLogDao = db.doseLogDao()

    @Provides
    fun provideCaregiverDao(db: AppDatabase): CaregiverDao = db.caregiverDao()

    @Provides
    fun provideLocalAiModelDao(db: AppDatabase) = db.localAiModelDao()

    @Provides
    fun provideFamilyMemberDao(db: AppDatabase): FamilyMemberDao = db.familyMemberDao()

    @Provides
    @Singleton
    fun provideMedicationRepository(
        database: AppDatabase,
        medicationDao: MedicationDao,
        scheduleDao: ScheduleDao,
        doseLogDao: DoseLogDao,
        caregiverDao: CaregiverDao,
        familyMemberDao: FamilyMemberDao
    ): MedicationRepository = MedicationRepositoryImpl(
        database, medicationDao, scheduleDao, doseLogDao, caregiverDao, familyMemberDao
    )
}
