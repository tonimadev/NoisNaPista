package digital.tonima.noisnapista.core.location

import digital.tonima.noisnapista.core.model.LocationPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

class DummyLocationProvider @Inject constructor() : LocationProvider {
    override fun getLocationUpdates(): Flow<LocationPoint> = emptyFlow()
    override suspend fun getCurrentLocation(): LocationPoint? = null
}
