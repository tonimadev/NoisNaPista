package com.ipirangatech.fidd.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "potholes")
data class PotholeEntity(
    @PrimaryKey val id: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val severity: Float,
    val timestamp: Long,
    val isSynced: Boolean = false,
    val isFalseAlarm: Boolean = false,
    val serverId: String? = null,
    val status: String? = null,
    val sessionId: String? = null,
)
