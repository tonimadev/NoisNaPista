package com.ipirangatech.fidd.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorWindowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(window: SensorWindowEntity)

    @Query("SELECT COUNT(*) FROM sensor_windows")
    suspend fun count(): Int

    /** Debug-screen source: every captured window, newest first, kept live so label edits and
     * new captures during a drive both reflect immediately without a manual refresh. */
    @Query("SELECT * FROM sensor_windows ORDER BY capturedAt DESC")
    fun getAllWindows(): Flow<List<SensorWindowEntity>>

    @Query("UPDATE sensor_windows SET label = :label WHERE potholeId = :potholeId")
    suspend fun updateLabel(
        potholeId: String,
        label: String,
    )

    @Query("UPDATE sensor_windows SET note = :note WHERE potholeId = :potholeId")
    suspend fun updateNote(
        potholeId: String,
        note: String,
    )
}
