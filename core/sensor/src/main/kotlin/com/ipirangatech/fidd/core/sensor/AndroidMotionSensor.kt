package com.ipirangatech.fidd.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.RotationRateSample
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

class AndroidMotionSensor
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : MotionSensor {
        private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Both virtual sensors: fused from the accelerometer + gyroscope (and sometimes the
        // magnetometer). Null on devices that lack a gyroscope — accelerationFlow()/the rotation flow
        // below fall back to an empty Flow in that case, and PotholeDetector already treats "no
        // reading yet" as "unknown, fail open" for exactly this reason.
        private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        private val gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        override fun getAccelerationUpdates(): Flow<AccelerationSample> = accelerationFlow(accelerometer)

        override fun getGravityUpdates(): Flow<AccelerationSample> = accelerationFlow(gravitySensor)

        override fun getRotationRateUpdates(): Flow<RotationRateSample> {
            val sensor = gyroscope ?: return emptyFlow()
            return callbackFlow {
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
                                trySend(
                                    RotationRateSample(
                                        x = event.values[0],
                                        y = event.values[1],
                                        z = event.values[2],
                                        timestampNanos = event.timestamp,
                                    ),
                                )
                            }
                        }

                        override fun onAccuracyChanged(
                            sensor: Sensor?,
                            accuracy: Int,
                        ) {
                            // Accuracy changes don't affect detection: every sample is used as is.
                        }
                    }

                sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
                awaitClose { sensorManager.unregisterListener(listener) }
            }
        }

        /** Shared plumbing for the two 3-axis, m/s² sensors (raw accelerometer and gravity) — same
         * event shape, just a different underlying Sensor. */
        private fun accelerationFlow(sensor: Sensor?): Flow<AccelerationSample> {
            val target = sensor ?: return emptyFlow()
            return callbackFlow {
                val listener =
                    object : SensorEventListener {
                        override fun onSensorChanged(event: SensorEvent?) {
                            if (event?.sensor?.type == target.type) {
                                trySend(
                                    AccelerationSample(
                                        x = event.values[0],
                                        y = event.values[1],
                                        z = event.values[2],
                                        timestampNanos = event.timestamp,
                                    ),
                                )
                            }
                        }

                        override fun onAccuracyChanged(
                            sensor: Sensor?,
                            accuracy: Int,
                        ) {
                            // Accuracy changes don't affect detection: every sample is used as is.
                        }
                    }

                // SENSOR_DELAY_GAME (~50Hz) instead of _NORMAL (~5Hz): the live sensor bar stays
                // smooth, and captured windows (see PotholeDetector) have enough time resolution to be
                // useful for offline model training later, not just a single peak value.
                sensorManager.registerListener(listener, target, SensorManager.SENSOR_DELAY_GAME)
                awaitClose { sensorManager.unregisterListener(listener) }
            }
        }
    }
