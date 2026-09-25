package com.ipirangatech.fidd.core.sensor

import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.RotationRateSample
import kotlinx.coroutines.flow.Flow

interface MotionSensor {
    fun getAccelerationUpdates(): Flow<AccelerationSample>

    /** Device-frame gravity vector (virtual sensor, fused from the accelerometer and gyroscope;
     * magnitude ~9.8 m/s², direction follows however the phone is currently oriented). Lets
     * PotholeDetector work out which way is really "down" regardless of mounting angle, instead
     * of assuming the raw accelerometer's Z axis is always vertical. Empty flow on devices without
     * this sensor — callers must treat "no reading yet" as "unknown", not "zero". */
    fun getGravityUpdates(): Flow<AccelerationSample>

    /** Raw gyroscope rotation rate (rad/s per axis). Empty flow on devices without a gyroscope. */
    fun getRotationRateUpdates(): Flow<RotationRateSample>
}
