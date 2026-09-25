package com.ipirangatech.fidd.core.data

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface DataModule {
    @Binds
    fun bindPotholeRepository(impl: PotholeRepositoryImpl): PotholeRepository

    @Binds
    fun bindCityRepository(impl: CityRepositoryImpl): CityRepository

    companion object {
        @Provides
        @Singleton
        fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "fidd.db"
            )
                // Pre-release app, no shipped schema to preserve yet.
                .fallbackToDestructiveMigration(true)
                .build()
        }

        @Provides
        fun providePotholeDao(database: AppDatabase): PotholeDao {
            return database.potholeDao()
        }

        @Provides
        fun provideSensorWindowDao(database: AppDatabase): SensorWindowDao {
            return database.sensorWindowDao()
        }
    }
}
