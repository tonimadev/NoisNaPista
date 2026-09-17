package digital.tonima.noisnapista.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LocationPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: Long
)
