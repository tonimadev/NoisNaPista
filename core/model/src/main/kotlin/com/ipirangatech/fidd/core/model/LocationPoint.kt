package com.ipirangatech.fidd.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long,
    /** Ground speed in m/s reported by the location provider, or null when the provider didn't
     * supply one (e.g. a stale/degraded fix). Used to skip pothole detection while stationary. */
    val speed: Float? = null,
)
