package digital.tonima.noisnapista.core.sensor

import digital.tonima.noisnapista.core.model.AccelerationSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

class DummyAccelerometerSensor @Inject constructor() : AccelerometerSensor {
    override fun getAccelerationUpdates(): Flow<AccelerationSample> = emptyFlow()
}
