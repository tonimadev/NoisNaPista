package digital.tonima.noisnapista.core.sensor

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {
    @Binds
    abstract fun bindAccelerometerSensor(impl: AndroidAccelerometerSensor): AccelerometerSensor
}
