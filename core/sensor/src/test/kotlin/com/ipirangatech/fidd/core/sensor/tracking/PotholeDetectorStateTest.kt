package com.ipirangatech.fidd.core.sensor.tracking

import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.model.SensorWindow
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.testLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Lifecycle, cooldown and sensor-window capture of [PotholeDetector], driven event by event
 * through fake sensors (the gate logic itself is covered by PotholeDetectorTest).
 */
class PotholeDetectorStateTest {
    private val motion = FakeMotionSensor()
    private val location = FakeLocationProvider()
    private val detector = PotholeDetector(motion, location)

    @After
    fun tearDown() = detector.stopDetection()

    private fun sample(
        z: Float,
        atMillis: Long,
        x: Float = 0f,
        y: Float = 0f,
    ) = AccelerationSample(x, y, z, timestampNanos = atMillis * 1_000_000)

    /** Starts detection with a moving GPS fix and waits until the accelerometer is being collected. */
    private suspend fun startMoving() {
        location.updates.emit(testLocation(speed = 10f))
        detector.startDetection()
        withTimeout(2.seconds) {
            motion.acceleration.subscriptionCount.first { it > 0 }
            detector.currentLocation.first { it != null }
        }
    }

    private fun CoroutineScope.collectPotholes(count: Int) =
        async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(2.seconds) { detector.potholes.take(count).toList() }
        }

    @Test
    fun `start and stop drive isTracking and reset the live readings`() =
        runBlocking {
            assertFalse(detector.isTracking.value)
            startMoving()
            assertTrue(detector.isTracking.value)

            motion.acceleration.emit(sample(z = 3f, atMillis = 0, x = 1f, y = 2f))
            withTimeout(2.seconds) { detector.currentAcceleration.first { it.z == 3f } }
            assertEquals(3f, detector.currentVerticalAcceleration.value)

            detector.stopDetection()
            assertFalse(detector.isTracking.value)
            assertNull(detector.currentLocation.value)
            assertEquals(AccelerationSample(0f, 0f, 0f, 0L), detector.currentAcceleration.value)
            assertEquals(0f, detector.currentVerticalAcceleration.value)
        }

    @Test
    fun `starting twice keeps a single run`() =
        runBlocking {
            startMoving()
            detector.startDetection()

            assertEquals(1, motion.acceleration.subscriptionCount.value)
        }

    @Test
    fun `no GPS fix means no detection`() =
        runBlocking {
            detector.startDetection()
            withTimeout(2.seconds) { motion.acceleration.subscriptionCount.first { it > 0 } }
            val result = collectPotholes(1)

            motion.acceleration.emit(sample(z = 30f, atMillis = 0))

            assertNull(result.await())
        }

    @Test
    fun `readings at or below the threshold never trigger`() =
        runBlocking {
            startMoving()
            val result = collectPotholes(1)

            motion.acceleration.emit(sample(z = 15f, atMillis = 0))
            motion.acceleration.emit(sample(z = 9.8f, atMillis = 10))

            assertNull(result.await())
        }

    @Test
    fun `a suspension bounce inside the cooldown is not a second pothole, a later bump is`() =
        runBlocking {
            startMoving()
            val result = collectPotholes(2)

            motion.acceleration.emit(sample(z = 20f, atMillis = 0))
            motion.acceleration.emit(sample(z = 25f, atMillis = 2_000)) // bounce, 2s later
            motion.acceleration.emit(sample(z = 22f, atMillis = 6_000)) // new bump, past the 4s cooldown

            val potholes: List<Pothole> = result.await()!!
            assertEquals(listOf(20f, 22f), potholes.map { it.severity })
            assertEquals(testLocation(speed = 10f), potholes[0].location)
            assertEquals("both detections belong to the same trip", potholes[0].sessionId, potholes[1].sessionId)
        }

    @Test
    fun `each run gets its own session id`() =
        runBlocking {
            startMoving()
            val first = collectPotholes(1)
            motion.acceleration.emit(sample(z = 20f, atMillis = 0))
            val firstSession = first.await()!!.single().sessionId
            detector.stopDetection()

            startMoving()
            val second = collectPotholes(1)
            motion.acceleration.emit(sample(z = 20f, atMillis = 60_000))
            val secondSession = second.await()!!.single().sessionId

            assertNotEquals(firstSession, secondSession)
        }

    @Test
    fun `a captured window holds the samples around the impact with relative offsets`() =
        runBlocking {
            startMoving()
            val window =
                async(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                    withTimeout(5.seconds) { detector.sensorWindows.first() }
                }

            motion.acceleration.emit(sample(z = 1f, atMillis = 7_000)) // too old: >2s before impact
            motion.acceleration.emit(sample(z = 9f, atMillis = 9_500))
            motion.acceleration.emit(sample(z = 20f, atMillis = 10_000)) // impact
            motion.acceleration.emit(sample(z = 12f, atMillis = 10_500))
            motion.acceleration.emit(sample(z = 5f, atMillis = 11_500)) // >1s after impact

            val captured: SensorWindow = window.await()
            assertEquals(listOf(-500L, 0L, 500L), captured.samples.map { it.offsetMs })
            assertEquals(listOf(9f, 20f, 12f), captured.samples.map { it.z })
        }

    @Test
    fun `an unusable gravity reading falls back to the raw Z axis`() =
        runBlocking {
            motion.gravity.emit(AccelerationSample(0.1f, 0f, 0f, 0L)) // magnitude below 1 m/s²
            startMoving()
            withTimeout(2.seconds) { motion.gravity.subscriptionCount.first { it > 0 } }

            motion.acceleration.emit(sample(z = -7f, atMillis = 0, x = 50f))

            assertEquals(7f, withTimeout(2.seconds) { detector.currentVerticalAcceleration.first { it == 7f } })
        }

    @Test
    fun `sensor or GPS stream errors don't stop detection`() =
        runBlocking {
            val failing =
                PotholeDetector(
                    motionSensor =
                        object : com.ipirangatech.fidd.core.sensor.MotionSensor by motion {
                            override fun getGravityUpdates() =
                                kotlinx.coroutines.flow.flow<AccelerationSample> { error("no gravity") }

                            override fun getRotationRateUpdates() =
                                kotlinx.coroutines.flow.flow<com.ipirangatech.fidd.core.model.RotationRateSample> {
                                    error("no gyro")
                                }
                        },
                    locationProvider =
                        object : com.ipirangatech.fidd.core.location.LocationProvider by location {
                            override fun getLocationUpdates() =
                                kotlinx.coroutines.flow.flow<com.ipirangatech.fidd.core.model.LocationPoint> {
                                    throw SecurityException("revoked")
                                }
                        },
                )
            failing.startDetection()
            withTimeout(2.seconds) { motion.acceleration.subscriptionCount.first { it > 0 } }

            motion.acceleration.emit(sample(z = 4f, atMillis = 0))

            withTimeout(2.seconds) { failing.currentVerticalAcceleration.first { it == 4f } }
            assertTrue(failing.isTracking.value)
            failing.stopDetection()
        }
}
