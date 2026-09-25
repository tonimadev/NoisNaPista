package com.ipirangatech.fidd.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [PotholeEntity::class, SensorWindowEntity::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun potholeDao(): PotholeDao

    abstract fun sensorWindowDao(): SensorWindowDao
}
