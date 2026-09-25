package com.ipirangatech.fidd.core.sensor.tracking

import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.RotationRateSample
import com.ipirangatech.fidd.core.sensor.MotionSensor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Verifies the speed/rotation gates and the gravity-corrected vertical-acceleration trigger,
 * without needing an emulator to fake sensor+GPS hardware: real hardware doesn't support `adb emu`
 * sensor/geo injection at all, and only a physical device was available when this was written.
 */
class PotholeDetectorTest {
    private fun fakeLocationProvider(speed: Float?) =
        object : LocationProvider {
            override fun getLocationUpdates(): Flow<LocationPoint> =
                flow {
                    emit(LocationPoint(latitude = 0.0, longitude = 0.0, accuracy = 5f, timestamp = 0L, speed = speed))
                    awaitCancellation() // keeps the fix "current" instead of completing the collector
                }

            override suspend fun getCurrentLocation(): LocationPoint? = null
        }

    private fun fakeMotionSensor(
        accelSample: AccelerationSample,
        gravitySample: AccelerationSample? = null,
        rotationSample: RotationRateSample? = null,
    ) = object : MotionSensor {
        override fun getAccelerationUpdates(): Flow<AccelerationSample> =
            flow {
                delay(150.milliseconds) // give gravity/rotation/location above time to populate first
                emit(accelSample)
            }

        override fun getGravityUpdates(): Flow<AccelerationSample> =
            gravitySample?.let {
                flow {
                    emit(it)
                    awaitCancellation()
                }
            } ?: emptyFlow()

        override fun getRotationRateUpdates(): Flow<RotationRateSample> =
            rotationSample?.let {
                flow {
                    emit(it)
                    awaitCancellation()
                }
            } ?: emptyFlow()
    }

    // > IMPACT_THRESHOLD (15f)
    private val impactSample = AccelerationSample(x = 0f, y = 0f, z = 20f, timestampNanos = 0L)

    @Test
    fun `does not detect an impact while stationary`() =
        runBlocking {
            val detector = PotholeDetector(fakeMotionSensor(impactSample), fakeLocationProvider(speed = 0.5f))
            detector.startDetection()
            val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
            detector.stopDetection()
            assertNull("stationary (0.5 m/s < MIN_SPEED_MPS) should not emit a detection", pothole)
        }

    @Test
    fun `detects an impact while moving`() =
        runBlocking {
            val detector = PotholeDetector(fakeMotionSensor(impactSample), fakeLocationProvider(speed = 3f))
            detector.startDetection()
            val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
            detector.stopDetection()
            assertNotNull("moving (3 m/s > MIN_SPEED_MPS) should emit a detection", pothole)
        }

    @Test
    fun `detects an impact when speed is unknown (fails open)`() =
        runBlocking {
            val detector = PotholeDetector(fakeMotionSensor(impactSample), fakeLocationProvider(speed = null))
            detector.startDetection()
            val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
            detector.stopDetection()
            assertNotNull("unknown speed must not block detection", pothole)
        }

    @Test
    fun `detects an impact via the gravity-corrected vertical axis when the phone is mounted sideways`() =
        runBlocking {
            // Gravity points along X instead of Z here — as if the phone were mounted rotated 90°.
            // A real bump's energy would then show up on X too, so raw Z stays ~0 the whole time and
            // would never cross IMPACT_THRESHOLD under the old "abs(z)" logic.
            val gravitySample = AccelerationSample(x = 9.8f, y = 0f, z = 0f, timestampNanos = 0L)
            // gravity + ~20 m/s² bump, all on X
            val sidewaysImpact = AccelerationSample(x = 30f, y = 0f, z = 0f, timestampNanos = 0L)
            val detector =
                PotholeDetector(
                    fakeMotionSensor(sidewaysImpact, gravitySample = gravitySample),
                    fakeLocationProvider(speed = 3f),
                )
            detector.startDetection()
            val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
            detector.stopDetection()
            assertNotNull(
                "a bump aligned with a non-vertical raw axis should still be detected once corrected for orientation",
                pothole,
            )
        }

    @Test
    fun `does not detect an impact while the gyroscope shows the phone is being handled`() =
        runBlocking {
            // > HANDLING_ROTATION_THRESHOLD_RAD_S (12f)
            val highRotation = RotationRateSample(x = 0f, y = 0f, z = 20f, timestampNanos = 0L)
            val detector =
                PotholeDetector(
                    fakeMotionSensor(impactSample, rotationSample = highRotation),
                    fakeLocationProvider(speed = 3f),
                )
            detector.startDetection()
            val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
            detector.stopDetection()
            assertNull("high rotation rate (phone being picked up/turned) should not emit a detection", pothole)
        }
}
