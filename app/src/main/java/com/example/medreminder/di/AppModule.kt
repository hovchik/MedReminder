package com.example.medreminder.di

import android.content.Context
import androidx.room.Room
import com.example.medreminder.data.local.MedReminderDatabase
import com.example.medreminder.data.local.MedicationDao
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
    fun provideDatabase(@ApplicationContext context: Context): MedReminderDatabase {
        return Room.databaseBuilder(
            context,
            MedReminderDatabase::class.java,
            "med_reminder_db"
        ).build()
    }

    @Provides
    @Singleton
    fun provideMedicationDao(database: MedReminderDatabase): MedicationDao {
        return database.medicationDao()
    }
}
