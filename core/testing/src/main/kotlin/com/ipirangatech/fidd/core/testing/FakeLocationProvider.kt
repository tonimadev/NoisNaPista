package com.ipirangatech.fidd.core.testing

import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.LocationPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeLocationProvider(var currentLocation: LocationPoint? = null) : LocationProvider {
    /** Emit into this to simulate GPS fixes while tracking. */
    val updates = MutableSharedFlow<LocationPoint>(replay = 1)
    var currentLocationRequests = 0

    override fun getLocationUpdates(): Flow<LocationPoint> = updates

    override suspend fun getCurrentLocation(): LocationPoint? {
        currentLocationRequests++
        return currentLocation
    }
}
