package com.ipirangatech.fidd.core.testing

import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.RotationRateSample
import com.ipirangatech.fidd.core.sensor.MotionSensor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Emit into the flows to simulate sensor events; nothing is emitted until a test does. */
class FakeMotionSensor : MotionSensor {
    val acceleration = MutableSharedFlow<AccelerationSample>(extraBufferCapacity = 256)
    val gravity = MutableSharedFlow<AccelerationSample>(replay = 1)
    val rotation = MutableSharedFlow<RotationRateSample>(replay = 1)

    override fun getAccelerationUpdates(): Flow<AccelerationSample> = acceleration

    override fun getGravityUpdates(): Flow<AccelerationSample> = gravity

    override fun getRotationRateUpdates(): Flow<RotationRateSample> = rotation
}
