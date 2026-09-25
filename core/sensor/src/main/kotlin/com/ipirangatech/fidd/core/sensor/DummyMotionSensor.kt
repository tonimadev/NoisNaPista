package com.ipirangatech.fidd.core.sensor

import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.RotationRateSample
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

class DummyMotionSensor @Inject constructor() : MotionSensor {
    override fun getAccelerationUpdates(): Flow<AccelerationSample> = emptyFlow()
    override fun getGravityUpdates(): Flow<AccelerationSample> = emptyFlow()
    override fun getRotationRateUpdates(): Flow<RotationRateSample> = emptyFlow()
}
