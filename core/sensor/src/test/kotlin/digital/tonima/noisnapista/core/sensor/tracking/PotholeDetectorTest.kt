package digital.tonima.noisnapista.core.sensor.tracking

import digital.tonima.noisnapista.core.location.LocationProvider
import digital.tonima.noisnapista.core.model.AccelerationSample
import digital.tonima.noisnapista.core.model.LocationPoint
import digital.tonima.noisnapista.core.sensor.AccelerometerSensor
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

/**
 * Verifies the speed gate added to skip detection while stationary (parked / phone handled
 * without the vehicle moving), without needing an emulator to fake sensor+GPS hardware: real
 * hardware doesn't support `adb emu` sensor/geo injection at all, and only a physical device was
 * available when this was written.
 */
class PotholeDetectorTest {

    private fun fakeLocationProvider(speed: Float?) = object : LocationProvider {
        override fun getLocationUpdates(): Flow<LocationPoint> = flow {
            emit(LocationPoint(latitude = 0.0, longitude = 0.0, accuracy = 5f, timestamp = 0L, speed = speed))
            awaitCancellation() // keeps the fix "current" instead of completing the collector
        }

        override suspend fun getCurrentLocation(): LocationPoint? = null
    }

    private fun fakeAccelerometer(sample: AccelerationSample) = object : AccelerometerSensor {
        override fun getAccelerationUpdates(): Flow<AccelerationSample> = flow {
            delay(100.milliseconds) // give the location flow above time to populate currentLocation first
            emit(sample)
        }
    }

    private val impactSample = AccelerationSample(x = 0f, y = 0f, z = 20f, timestampNanos = 0L) // > IMPACT_THRESHOLD (15f)

    @Test
    fun `does not detect an impact while stationary`() = runBlocking {
        val detector = PotholeDetector(fakeAccelerometer(impactSample), fakeLocationProvider(speed = 0.5f))
        detector.startDetection()
        val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
        detector.stopDetection()
        assertNull("stationary (0.5 m/s < MIN_SPEED_MPS) should not emit a detection", pothole)
    }

    @Test
    fun `detects an impact while moving`() = runBlocking {
        val detector = PotholeDetector(fakeAccelerometer(impactSample), fakeLocationProvider(speed = 3f))
        detector.startDetection()
        val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
        detector.stopDetection()
        assertNotNull("moving (3 m/s > MIN_SPEED_MPS) should emit a detection", pothole)
    }

    @Test
    fun `detects an impact when speed is unknown (fails open)`() = runBlocking {
        val detector = PotholeDetector(fakeAccelerometer(impactSample), fakeLocationProvider(speed = null))
        detector.startDetection()
        val pothole = withTimeoutOrNull(1000.milliseconds) { detector.potholes.first() }
        detector.stopDetection()
        assertNotNull("unknown speed must not block detection", pothole)
    }
}
