package com.ipirangatech.fidd.core.model

/**
 * One raw, live accelerometer reading (all three axes). Purely an in-memory streaming type —
 * never persisted directly, see [SensorWindow] for the captured/stored shape.
 *
 * [timestampNanos] is the sensor event's own timebase (elapsed-realtime nanoseconds); only
 * meaningful as a relative delta between samples of the same stream, never as a wall-clock value.
 */
data class AccelerationSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long,
)
