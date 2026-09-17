package digital.tonima.noisnapista.core.sensor

import digital.tonima.noisnapista.core.model.AccelerationSample
import kotlinx.coroutines.flow.Flow

interface AccelerometerSensor {
    fun getAccelerationUpdates(): Flow<AccelerationSample>
}
