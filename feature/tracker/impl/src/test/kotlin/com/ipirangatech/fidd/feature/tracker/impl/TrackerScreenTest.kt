package com.ipirangatech.fidd.feature.tracker.impl

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.data.OnboardingPreferences
import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import com.ipirangatech.fidd.core.sensor.tracking.TrackingService
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import com.ipirangatech.fidd.core.testing.awaitFirst
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h3000dp")
class TrackerScreenTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val motion = FakeMotionSensor()
    private val location = FakeLocationProvider()
    private val detector = PotholeDetector(motion, location)
    private val repository = FakePotholeRepository()
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var viewModel: TrackerViewModel

    @After
    fun tearDown() {
        detector.stopDetection()
        dataStoreScope.cancel()
    }

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun grant(vararg permissions: String) = shadowOf(app).grantPermissions(*permissions)

    private fun setContent(showEmbeddedMap: Boolean = false, startedBefore: Boolean = true) {
        val preferences = OnboardingPreferences(testPreferencesDataStore(dataStoreScope, tmp.root))
        if (startedBefore) runBlocking { preferences.markDetectionStarted() }
        viewModel = TrackerViewModel(app, detector, location, repository, preferences)
        compose.setContent { TrackerScreen(viewModel = viewModel, showEmbeddedMap = showEmbeddedMap) }
    }

    private fun waitForText(text: String) = compose.waitUntil(3_000) {
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Answers the pending runtime-permission request as the system dialog would. */
    @Suppress("DEPRECATION") // the Activity Result API delivers permission results through this callback
    private fun answerPermissionRequest(vararg granted: Boolean) {
        val request = shadowOf(compose.activity).lastRequestedPermission
        request.requestedPermissions.zip(granted.toList()).filter { it.second }.forEach { grant(it.first) }
        compose.activity.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            granted.map { if (it) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED }.toIntArray()
        )
        compose.waitForIdle()
    }

    private fun startedService() = shadowOf(app).nextStartedService?.component?.className

    @Test
    fun `first run without location explains how it works and why location is needed`() {
        setContent(startedBefore = false)

        compose.onNodeWithText(str(R.string.tracker_status_off_title)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_status_off_needs_location_body)).assertExists()
        waitForText(str(R.string.tracker_how_it_works_title))
        compose.onNodeWithText(str(R.string.common_no_pothole_detected)).assertExists()
    }

    @Test
    fun `tapping start without permission shows the rationale, which can be dismissed`() {
        setContent()

        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()
        compose.onNodeWithText(str(R.string.tracker_permission_rationale_title)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_permission_not_now)).performClick()

        compose.onNodeWithText(str(R.string.tracker_permission_rationale_title)).assertDoesNotExist()
        assertNull(startedService())
    }

    @Test
    fun `granting precise location then notifications starts tracking`() {
        setContent()

        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()
        compose.onNodeWithText(str(R.string.tracker_permission_rationale_confirm)).performClick()
        answerPermissionRequest(true, true) // fine + coarse
        assertEquals(Manifest.permission.POST_NOTIFICATIONS, shadowOf(compose.activity).lastRequestedPermission.requestedPermissions.single())
        answerPermissionRequest(false) // notifications are optional

        assertEquals(TrackingService::class.java.name, startedService())
    }

    @Test
    fun `approximate-only location asks for precise location`() {
        setContent()
        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()
        compose.onNodeWithText(str(R.string.tracker_permission_rationale_confirm)).performClick()

        answerPermissionRequest(false, true)

        compose.onNodeWithText(str(R.string.tracker_permission_precise_title)).assertExists()
        shadowOf(app.packageManager).setShouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION, true)
        compose.onNodeWithText(str(R.string.tracker_permission_precise_confirm)).performClick()
        compose.waitForIdle()
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
            shadowOf(compose.activity).lastRequestedPermission.requestedPermissions.toList()
        )
    }

    @Test
    fun `approximate-only location that can't be asked again sends the user to settings`() {
        setContent()
        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()
        compose.onNodeWithText(str(R.string.tracker_permission_rationale_confirm)).performClick()
        answerPermissionRequest(false, true)

        compose.onNodeWithText(str(R.string.tracker_permission_precise_confirm)).performClick()

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun `a plain denial is respected without a dialog`() {
        setContent()
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).forEach {
            shadowOf(compose.activity.packageManager).setShouldShowRequestPermissionRationale(it, true)
        }
        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()
        compose.onNodeWithText(str(R.string.tracker_permission_rationale_confirm)).performClick()

        answerPermissionRequest(false, false)

        compose.onNodeWithText(str(R.string.tracker_permission_blocked_title)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.tracker_permission_precise_title)).assertDoesNotExist()
    }

    @Test
    fun `a blocked permission explains how to re-enable it in settings`() {
        setContent()
        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()
        compose.onNodeWithText(str(R.string.tracker_permission_rationale_confirm)).performClick()

        answerPermissionRequest(false, false)
        compose.onNodeWithText(str(R.string.tracker_permission_blocked_title)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_permission_open_settings)).performClick()

        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun `with every permission granted start goes straight to the service`() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS)
        location.currentLocation = testLocation()
        setContent()

        compose.onNodeWithText(str(R.string.tracker_status_off_body)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_start_button)).performClick()

        assertEquals(TrackingService::class.java.name, startedService())
    }

    @Test
    fun `while tracking the card offers pause, which stops the service`() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        setContent()
        detector.startDetection()
        waitForText(str(R.string.tracker_status_on_title))

        compose.onNodeWithText(str(R.string.tracker_status_on_body)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_stop_button)).performClick()

        assertEquals(TrackingService::class.java.name, shadowOf(app).nextStoppedService.component?.className)
    }

    @Test
    fun `recent detections are counted and labelled by severity band`() {
        repository.potholes.value = listOf(
            testPothole(id = "strong", severity = 30f),
            testPothole(id = "medium", severity = 15f),
            testPothole(id = "light", severity = 8f)
        )
        setContent()

        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.tracker_pothole_count_format, 3, 3)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_severity_high)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_severity_medium)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_severity_low)).assertExists()
    }

    @Test
    fun `the sensor panel shows placeholders when idle and live readings while tracking`() {
        setContent()
        compose.onNodeWithText(str(R.string.tracker_sensor_details_toggle)).performClick()

        compose.onNodeWithText(str(R.string.tracker_waiting_gps)).assertExists()
        compose.onNodeWithText(str(R.string.tracker_gps_inactive)).assertExists()
        compose.onNodeWithText(str(R.string.common_placeholder_dash)).assertExists()

        runBlocking { location.updates.emit(testLocation(speed = 10f)) }
        detector.startDetection()
        waitForText(str(R.string.tracker_gps_active))
        waitForText(str(R.string.tracker_lat_lon_format, -23.5505, -46.6333))
        compose.waitUntil(3_000) { motion.acceleration.subscriptionCount.value > 0 }

        runBlocking { motion.acceleration.emit(AccelerationSample(1f, 2f, 5f, 0L)) }
        waitForText(str(R.string.tracker_sensor_value_format, 5f))
        runBlocking { motion.acceleration.emit(AccelerationSample(0f, 0f, 12f, 10L)) }
        waitForText(str(R.string.tracker_sensor_value_format, 12f))
        runBlocking { motion.acceleration.emit(AccelerationSample(0f, 0f, 16f, 20L)) }
        waitForText(str(R.string.tracker_impact_label))
        compose.onRoot().captureToImage() // forces the axis gizmo to actually draw

        compose.onNodeWithText(str(R.string.tracker_sensor_details_toggle)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `errors from the view model are shown as a toast`() {
        setContent()

        viewModel.onIntent(TrackerUiIntent.StartTracking)
        compose.waitForIdle()

        assertEquals(str(R.string.tracker_location_permission_required), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `the embedded map composes without a location`() {
        repository.potholes.value = listOf(testPothole())
        setContent(showEmbeddedMap = true)

        compose.onNodeWithText(str(R.string.tracker_status_off_title)).assertExists()
    }

    @Test
    fun `the embedded map composes around the current location`() {
        // Set up before composing: once the map view exists it keeps the main looper busy, so
        // waiting for later state changes would time out.
        runBlocking {
            location.updates.emit(testLocation())
            detector.startDetection()
            detector.currentLocation.awaitFirst { it != null }
        }
        setContent(showEmbeddedMap = true)

        compose.onNodeWithText(str(R.string.tracker_status_on_title)).assertExists()
    }
}
