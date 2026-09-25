package com.ipirangatech.fidd.core.location

import android.content.Context
import android.location.Location
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.ipirangatech.fidd.core.model.LocationPoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The fused client is a Play Services binder — faked here so the mapping and lifecycle can be checked on the JVM. */
@RunWith(RobolectricTestRunner::class)
class AndroidLocationProviderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val client: FusedLocationProviderClient = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(LocationServices::class)
        every { LocationServices.getFusedLocationProviderClient(any<Context>()) } returns client
    }

    @After
    fun tearDown() = unmockkAll()

    private fun location(speed: Float? = null) =
        Location("fused").apply {
            latitude = -23.5
            longitude = -46.6
            accuracy = 4f
            time = 123L
            speed?.let { this.speed = it }
        }

    @Test
    fun `location updates are mapped to points and the callback is removed on cancel`() =
        runTest(UnconfinedTestDispatcher()) {
            val callback = slot<LocationCallback>()
            every {
                client.requestLocationUpdates(
                    any<LocationRequest>(),
                    capture(callback),
                    any<Looper>(),
                )
            } returns mockk()
            val received = mutableListOf<LocationPoint>()

            val job = launch { AndroidLocationProvider(context).getLocationUpdates().collect { received += it } }
            callback.captured.onLocationResult(LocationResult.create(listOf(location(speed = 12f))))
            callback.captured.onLocationResult(LocationResult.create(listOf(location())))
            job.cancel()

            assertEquals(LocationPoint(-23.5, -46.6, 4f, 123L, speed = 12f), received[0])
            assertNull("a fix without speed must not report 0 m/s", received[1].speed)
            verify { client.removeLocationUpdates(callback.captured) }
        }

    @Test
    fun `a missing permission ends the update stream instead of crashing`() =
        runTest {
            every {
                client.requestLocationUpdates(any<LocationRequest>(), any<LocationCallback>(), any<Looper>())
            } throws SecurityException("denied")

            val result = runCatching { AndroidLocationProvider(context).getLocationUpdates().toList() }

            assertTrue(result.exceptionOrNull() is SecurityException)
        }

    private fun taskThat(
        succeedWith: Location? = null,
        fail: Boolean = false,
    ): Task<Location> {
        val task = mockk<Task<Location>>()
        every { task.addOnSuccessListener(any<OnSuccessListener<in Location>>()) } answers {
            if (!fail) firstArg<OnSuccessListener<in Location>>().onSuccess(succeedWith)
            task
        }
        every { task.addOnFailureListener(any<OnFailureListener>()) } answers {
            if (fail) firstArg<OnFailureListener>().onFailure(Exception("no fix"))
            task
        }
        return task
    }

    @Test
    fun `current location resolves the one-shot fix`() =
        runTest {
            every {
                client.getCurrentLocation(
                    any<Int>(),
                    any<CancellationToken>(),
                )
            } returns taskThat(location(speed = 3f))

            assertEquals(
                LocationPoint(-23.5, -46.6, 4f, 123L, 3f),
                AndroidLocationProvider(context).getCurrentLocation(),
            )
        }

    @Test
    fun `current location is null when there is no fix, the request fails, or permission is missing`() =
        runTest {
            val provider = AndroidLocationProvider(context)

            every {
                client.getCurrentLocation(
                    any<Int>(),
                    any<CancellationToken>(),
                )
            } returns taskThat(succeedWith = null)
            assertNull(provider.getCurrentLocation())

            every { client.getCurrentLocation(any<Int>(), any<CancellationToken>()) } returns taskThat(fail = true)
            assertNull(provider.getCurrentLocation())

            every { client.getCurrentLocation(any<Int>(), any<CancellationToken>()) } throws SecurityException("denied")
            assertNull(provider.getCurrentLocation())
        }

    @Test
    fun `the dummy provider has no location`() =
        runTest {
            val dummy = DummyLocationProvider()

            assertNull(dummy.getCurrentLocation())
            assertTrue(dummy.getLocationUpdates().toList().isEmpty())
        }
}
