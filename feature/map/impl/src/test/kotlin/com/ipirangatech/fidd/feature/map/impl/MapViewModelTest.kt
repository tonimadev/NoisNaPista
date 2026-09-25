package com.ipirangatech.fidd.feature.map.impl

import app.cash.turbine.test
import com.ipirangatech.fidd.core.model.GeoBounds
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.awaitFirst
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePotholeRepository()
    private val location = FakeLocationProvider()
    private val detector = PotholeDetector(FakeMotionSensor(), location)
    private val viewport = GeoBounds(-24.0, -47.0, -23.0, -46.0)

    @After
    fun tearDown() = detector.stopDetection()

    private fun viewModel() = MapViewModel(repository, location, detector)

    @Test
    fun `only active local potholes are shown`() =
        runTest {
            repository.potholes.value = listOf(testPothole(id = "a"), testPothole(id = "b", isFalseAlarm = true))

            viewModel().potholes.test {
                assertEquals(listOf("a"), awaitItem().map { it.id })
            }
        }

    @Test
    fun `before tracking the preview fix is the current location, then the live stream takes over`() =
        runTest {
            location.currentLocation = testLocation(latitude = -10.0)
            val vm = viewModel()

            vm.currentLocation.test {
                assertEquals(-10.0, awaitItem()!!.latitude, 0.0)
                location.updates.emit(testLocation(latitude = -20.0))
                detector.startDetection()
                assertEquals(-20.0, awaitItem()!!.latitude, 0.0)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `nothing is fetched until the map reports a viewport`() =
        runTest {
            val vm = viewModel()

            vm.refreshCommunityPotholes()

            assertTrue(repository.fetchedBounds.isEmpty())
        }

    @Test
    fun `viewport changes load community potholes and failures keep the previous pins silently`() =
        runTest {
            val vm = viewModel()
            repository.communityResult = Result.success(listOf(testPothole(id = "srv-1", serverId = "srv-1")))

            vm.uiEffect.test {
                vm.onViewportChanged(viewport)
                repository.communityResult = Result.failure(RuntimeException("offline"))
                vm.onViewportChanged(viewport)
                expectNoEvents()
            }
            assertEquals(listOf(viewport, viewport), repository.fetchedBounds)
            assertEquals(listOf("srv-1"), vm.communityPotholes.value.map { it.id })
        }

    @Test
    fun `a manual refresh reports failures but not superseded requests`() =
        runTest {
            val vm = viewModel()
            vm.onViewportChanged(viewport)

            vm.uiEffect.test {
                repository.communityResult = Result.failure(CancellationException("superseded"))
                vm.refreshCommunityPotholes()
                expectNoEvents()

                repository.communityResult = Result.failure(RuntimeException("offline"))
                vm.refreshCommunityPotholes()
                assertEquals(MapUiEffect.ShowMessage(R.string.map_community_refresh_failed), awaitItem())
            }
        }

    @Test
    fun `recenter uses the live fix while tracking`() =
        runTest {
            val vm = viewModel()
            location.updates.emit(testLocation(latitude = -5.0))
            detector.startDetection()
            detector.currentLocation.awaitFirst { it != null }

            vm.uiEffect.test {
                vm.recenter()
                assertEquals(MapUiEffect.CenterOn(testLocation(latitude = -5.0)), awaitItem())
            }
        }

    @Test
    fun `recenter fetches a fresh fix when idle, or explains that there is none`() =
        runTest {
            val vm = viewModel()

            vm.uiEffect.test {
                location.currentLocation = testLocation(latitude = -7.0)
                vm.recenter()
                assertEquals(MapUiEffect.CenterOn(testLocation(latitude = -7.0)), awaitItem())

                location.currentLocation = null
                vm.recenter()
                assertEquals(MapUiEffect.ShowMessage(R.string.map_location_unavailable), awaitItem())
            }
        }
}
