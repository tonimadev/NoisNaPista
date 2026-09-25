package com.ipirangatech.fidd.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SensorData(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestamp: Long
)
