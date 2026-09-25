package com.ipirangatech.fidd.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One raw sensor burst captured around a local detection (see core.model.SensorWindow), stored
 * as JSON in its own table — not a column on [PotholeEntity] — so routine list/map queries never
 * have to load this payload. Purely local: never synced to the backend, only exported manually
 * for offline labeling / model training.
 */
@Entity(tableName = "sensor_windows")
data class SensorWindowEntity(
    @PrimaryKey val potholeId: String,
    val samplesJson: String,
    val capturedAt: Long,
    /** Stores a [com.ipirangatech.fidd.core.model.DetectionLabel] name, assigned manually
     * from the debug/labeling screen. Defaults to "UNLABELED" for every existing/new capture. */
    val label: String = "UNLABELED",
    val note: String = ""
)
