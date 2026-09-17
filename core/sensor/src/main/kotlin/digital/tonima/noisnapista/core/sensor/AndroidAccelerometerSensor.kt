package digital.tonima.noisnapista.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.tonima.noisnapista.core.model.AccelerationSample
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject

class AndroidAccelerometerSensor @Inject constructor(
    @ApplicationContext private val context: Context
) : AccelerometerSensor {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    override fun getAccelerationUpdates(): Flow<AccelerationSample> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
                    trySend(
                        AccelerationSample(
                            x = event.values[0],
                            y = event.values[1],
                            z = event.values[2],
                            timestampNanos = event.timestamp
                        )
                    )
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // SENSOR_DELAY_GAME (~50Hz) instead of _NORMAL (~5Hz): the live sensor bar stays smooth,
        // and captured windows (see PotholeDetector) have enough time resolution to be useful
        // for offline model training later, not just a single peak value.
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
