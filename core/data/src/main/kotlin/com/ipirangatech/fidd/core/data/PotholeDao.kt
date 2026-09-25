package com.ipirangatech.fidd.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PotholeDao {
    @Query("SELECT * FROM potholes ORDER BY timestamp DESC")
    fun getAllPotholes(): Flow<List<PotholeEntity>>

    @Query("SELECT * FROM potholes WHERE isFalseAlarm = 0 ORDER BY timestamp DESC")
    fun getActivePotholes(): Flow<List<PotholeEntity>>

    @Query("SELECT * FROM potholes WHERE isSynced = 0")
    suspend fun getUnsyncedPotholes(): List<PotholeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPothole(pothole: PotholeEntity)

    @Query("UPDATE potholes SET isSynced = 1, serverId = :serverId, status = :status WHERE id = :id")
    suspend fun markAsSynced(id: String, serverId: String, status: String)

    @Query("UPDATE potholes SET isFalseAlarm = 1 WHERE id = :id")
    suspend fun markAsFalseAlarm(id: String)

    @androidx.room.Delete
    suspend fun delete(pothole: PotholeEntity)
}
