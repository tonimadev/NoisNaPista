package com.ipirangatech.fidd.feature.tracker.impl

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.ipirangatech.fidd.core.data.OnboardingPreferences
import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import com.ipirangatech.fidd.core.sensor.tracking.TrackingService
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.awaitCondition
import com.ipirangatech.fidd.core.testing.awaitFirst
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TrackerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val application: Application = ApplicationProvider.getApplicationContext()
    private val motion = FakeMotionSensor()
    private val location = FakeLocationProvider()
    private val detector = PotholeDetector(motion, location)
    private val repository = FakePotholeRepository()

    @After
    fun tearDown() = detector.stopDetection()

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        preferences: OnboardingPreferences = OnboardingPreferences(testPreferencesDataStore(backgroundScope, tmp.root))
    ) = TrackerViewModel(application, detector, location, repository, preferences)

    @Test
    fun `starting without location permission shows an error and starts nothing`() = runTest {
        val vm = viewModel()

        vm.uiEffect.test {
            vm.onIntent(TrackerUiIntent.StartTracking)
            assertEquals(TrackerUiEffect.ShowError(R.string.tracker_location_permission_required), awaitItem())
        }
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `start and stop drive the foreground tracking service`() = runTest {
        val vm = viewModel()
        vm.onIntent(TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.LOCATION, granted = true))

        vm.onIntent(TrackerUiIntent.StartTracking)
        assertEquals(TrackingService::class.java.name, shadowOf(application).nextStartedService.component?.className)

        vm.onIntent(TrackerUiIntent.StopTracking)
        assertEquals(TrackingService::class.java.name, shadowOf(application).nextStoppedService.component?.className)
    }

    @Test
    fun `permission toggles are reflected in the state`() = runTest {
        val vm = viewModel()

        vm.onIntent(TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.NOTIFICATION, granted = true))
        vm.onIntent(TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.LOCATION, granted = false))

        assertTrue(vm.uiState.value.notificationPermissionGranted)
        assertFalse(vm.uiState.value.locationPermissionGranted)
        assertEquals("denying location must not fetch a preview fix", 0, location.currentLocationRequests)
    }

    @Test
    fun `granting location shows a one-shot preview fix before tracking starts`() = runTest {
        location.currentLocation = testLocation(latitude = -22.9)
        val vm = viewModel()

        vm.onIntent(TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.LOCATION, granted = true))

        assertEquals(-22.9, vm.uiState.value.currentLocation!!.latitude, 0.0)
    }

    @Test
    fun `no preview fix is fetched while tracking is already running`() = runTest {
        location.currentLocation = testLocation()
        val vm = viewModel()
        detector.startDetection()
        vm.uiState.awaitFirst { it.isTracking }

        vm.onIntent(TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.LOCATION, granted = true))

        assertEquals(0, location.currentLocationRequests)
    }

    @Test
    fun `live detector state flows into the ui state and first tracking is remembered`() = runTest {
        val preferences = OnboardingPreferences(testPreferencesDataStore(backgroundScope, tmp.root))
        val vm = viewModel(preferences)
        vm.uiState.awaitFirst { !it.hasStartedDetectionBefore }

        location.updates.emit(testLocation(speed = 10f))
        detector.startDetection()
        motion.acceleration.subscriptionCount.awaitFirst { it > 0 }
        motion.acceleration.emit(AccelerationSample(1f, 2f, 3f, 0L))
        vm.uiState.awaitFirst { it.isTracking && it.sensorZ == 3f && it.currentLocation != null }
        vm.uiState.awaitFirst { it.hasStartedDetectionBefore }

        assertEquals(1f, vm.uiState.value.sensorX)
        assertEquals(2f, vm.uiState.value.sensorY)
        assertEquals(3f, vm.uiState.value.sensorIntensity)
        assertTrue(preferences.hasStartedDetection.first())
    }

    @Test
    fun `detections are saved with their sensor windows and shown in the state`() = runTest {
        val vm = viewModel()
        location.updates.emit(testLocation(speed = 10f))
        detector.startDetection()
        motion.acceleration.subscriptionCount.awaitFirst { it > 0 }
        detector.currentLocation.awaitFirst { it != null }
        motion.acceleration.emit(AccelerationSample(0f, 0f, 25f, 0L))
        vm.uiState.awaitFirst { it.detectedPotholes.isNotEmpty() }
        awaitCondition { repository.savedSensorWindows.isNotEmpty() }

        assertEquals(25f, vm.uiState.value.detectedPotholes.single().severity)
        assertEquals(vm.uiState.value.detectedPotholes.single().id, repository.savedSensorWindows.single().potholeId)
    }

    @Test
    fun `false alarms are not listed as detections`() = runTest {
        repository.potholes.value = listOf(testPothole(id = "a"), testPothole(id = "b", isFalseAlarm = true))

        assertEquals(listOf("a"), viewModel().uiState.value.detectedPotholes.map { it.id })
    }
}
