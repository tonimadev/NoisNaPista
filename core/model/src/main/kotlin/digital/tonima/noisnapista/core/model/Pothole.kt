package digital.tonima.noisnapista.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Pothole(
    val id: String,
    val location: LocationPoint,
    val severity: Float,
    val timestamp: Long,
    val isFalseAlarm: Boolean = false,
    /** Backend-assigned id once this report has been synced (or, for community-fetched
     * potholes, always set — those never have a local Room row). Null means "not synced yet". */
    val serverId: String? = null,
    /** Raw backend status ("PENDING"/"CONFIRMED"/"FIXED"), null for reports never synced. */
    val status: String? = null,
    /** How many distinct anonymous reporters corroborated this pothole on the backend. */
    val distinctReporterCount: Int = 1
)
