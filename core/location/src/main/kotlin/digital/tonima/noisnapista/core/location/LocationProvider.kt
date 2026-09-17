package digital.tonima.noisnapista.core.location

import digital.tonima.noisnapista.core.model.LocationPoint
import kotlinx.coroutines.flow.Flow

interface LocationProvider {
    fun getLocationUpdates(): Flow<LocationPoint>

    /** One-shot current-location fetch, for a single on-demand lookup (e.g. "find my city" on
     * the ranking screen) rather than the continuous stream [getLocationUpdates] provides.
     * Suspends until a fix is available or fails; returns null on permission denial, provider
     * error, or timeout — never throws. */
    suspend fun getCurrentLocation(): LocationPoint?
}
