package com.ipirangatech.fidd.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.RotationRateSample
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

@RunWith(RobolectricTestRunner::class)
class AndroidMotionSensorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val shadowManager = shadowOf(sensorManager)

    private fun addSensor(type: Int): Sensor = ShadowSensor.newInstance(type).also { shadowManager.addSensor(it) }

    private fun send(
        sensor: Sensor,
        x: Float,
        y: Float,
        z: Float,
        timestamp: Long,
    ) {
        shadowManager.sendSensorEventToListeners(
            SensorEventBuilder.newBuilder().setSensor(
                sensor,
            ).setValues(floatArrayOf(x, y, z)).setTimestamp(timestamp).build(),
        )
    }

    @Test
    fun `every stream is empty on a device without those sensors`() =
        runTest {
            val motionSensor = AndroidMotionSensor(context)

            assertTrue(motionSensor.getAccelerationUpdates().toList().isEmpty())
            assertTrue(motionSensor.getGravityUpdates().toList().isEmpty())
            assertTrue(motionSensor.getRotationRateUpdates().toList().isEmpty())
        }

    @Test
    fun `accelerometer and gravity events are mapped to samples and listeners are released`() =
        runTest(UnconfinedTestDispatcher()) {
            val accelerometer = addSensor(Sensor.TYPE_ACCELEROMETER)
            val gravity = addSensor(Sensor.TYPE_GRAVITY)
            val motionSensor = AndroidMotionSensor(context)
            val acceleration = mutableListOf<AccelerationSample>()
            val gravityReadings = mutableListOf<AccelerationSample>()

            val jobs =
                listOf(
                    launch { motionSensor.getAccelerationUpdates().collect { acceleration += it } },
                    launch { motionSensor.getGravityUpdates().collect { gravityReadings += it } },
                )
            send(accelerometer, 1f, 2f, 20f, timestamp = 42L)
            send(gravity, 0f, 0f, 9.8f, timestamp = 43L)

            assertEquals(listOf(AccelerationSample(1f, 2f, 20f, 42L)), acceleration)
            assertEquals(listOf(AccelerationSample(0f, 0f, 9.8f, 43L)), gravityReadings)
            jobs.forEach { it.cancel() }
            assertTrue(shadowManager.listeners.isEmpty())
        }

    @Test
    fun `gyroscope events are mapped to rotation samples`() =
        runTest(UnconfinedTestDispatcher()) {
            val gyroscope = addSensor(Sensor.TYPE_GYROSCOPE)
            val motionSensor = AndroidMotionSensor(context)
            val rotation = async { motionSensor.getRotationRateUpdates().first() }

            send(gyroscope, 0.1f, 0.2f, 0.3f, timestamp = 7L)

            assertEquals(RotationRateSample(0.1f, 0.2f, 0.3f, 7L), rotation.await())
        }

    @Test
    fun `the dummy sensor never emits`() =
        runTest {
            val dummy = DummyMotionSensor()

            assertTrue(dummy.getAccelerationUpdates().toList().isEmpty())
            assertTrue(dummy.getGravityUpdates().toList().isEmpty())
            assertTrue(dummy.getRotationRateUpdates().toList().isEmpty())
        }
}
