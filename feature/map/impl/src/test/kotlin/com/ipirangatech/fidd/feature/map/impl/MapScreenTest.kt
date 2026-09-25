package com.ipirangatech.fidd.feature.map.impl

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import com.google.android.gms.maps.CameraUpdateFactory
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Google Play services isn't available on the JVM, so the map itself never finishes loading here:
 * these tests cover everything around it (FABs, effects, marker list building), not the pins.
 */
@RunWith(RobolectricTestRunner::class)
class MapScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repository = FakePotholeRepository()
    private val location = FakeLocationProvider()
    private val detector = PotholeDetector(FakeMotionSensor(), location)

    @After
    fun tearDown() {
        detector.stopDetection()
        unmockkAll()
    }

    private fun setContent(): MapViewModel {
        val vm = MapViewModel(repository, location, detector)
        compose.setContent { MapScreen(viewModel = vm) }
        return vm
    }

    @Test
    fun `the refresh FAB refetches the last viewport and shows a snackbar on failure`() {
        val vm = setContent()
        vm.onViewportChanged(com.ipirangatech.fidd.core.model.GeoBounds(-24.0, -47.0, -23.0, -46.0))
        repository.communityResult = Result.failure(RuntimeException("offline"))

        compose.onNodeWithContentDescription(app.getString(R.string.map_refresh_community_cd)).performClick()

        compose.waitUntil(3_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(app.getString(R.string.map_community_refresh_failed)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(2, repository.fetchedBounds.size)
    }

    @Test
    fun `recentering without any fix explains why`() {
        setContent()

        compose.onNodeWithContentDescription(app.getString(R.string.map_recenter_cd)).performClick()

        compose.waitUntil(3_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasText(app.getString(R.string.map_location_unavailable)))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(1 + 1, location.currentLocationRequests) // preview on open + recenter
    }

    @Test
    fun `local and community potholes render and recentering animates the camera`() {
        // The real factory needs the Maps SDK initialized, which never happens without Play services.
        mockkStatic(CameraUpdateFactory::class)
        every { CameraUpdateFactory.newLatLngZoom(any(), any()) } returns mockk()
        location.currentLocation = testLocation()
        repository.potholes.value = listOf(testPothole(id = "a", serverId = "srv-a", severity = 25f))
        repository.communityResult = Result.success(
            listOf(testPothole(id = "srv-a", serverId = "srv-a"), testPothole(id = "srv-b", serverId = "srv-b", status = null))
        )
        val vm = setContent()
        vm.onViewportChanged(com.ipirangatech.fidd.core.model.GeoBounds(-24.0, -47.0, -23.0, -46.0))

        compose.waitForIdle()
        compose.onNodeWithContentDescription(app.getString(R.string.map_recenter_cd)).performClick()
        compose.waitForIdle()

        verify { CameraUpdateFactory.newLatLngZoom(any(), 16f) }
    }
}
