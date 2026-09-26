package com.ipirangatech.fidd.ads

import android.app.Activity
import app.cash.turbine.test
import com.ipirangatech.fidd.R
import com.ipirangatech.fidd.core.analytics.AnalyticsEvent
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import com.ipirangatech.fidd.core.testing.FakeAnalyticsTracker
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.FakeRemoveAdsRepository
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.awaitFirst
import io.mockk.mockk
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AdsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeRemoveAdsRepository(adsRemoved = false)
    private val detector = PotholeDetector(FakeMotionSensor(), FakeLocationProvider())
    private val activity: Activity = mockk()
    private val analytics = FakeAnalyticsTracker()

    @After
    fun tearDown() = detector.stopDetection()

    private fun viewModel() = AdsViewModel(repository, detector, analytics)

    @Test
    fun `connects to billing on creation`() {
        viewModel()
        assertEquals(1, repository.connectCalls)
    }

    @Test
    fun `shows ads to a user who has not bought the removal`() =
        runTest {
            val vm = viewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showAds.collect {} }
            assertTrue(vm.showAds.value)
        }

    @Test
    fun `hides ads while ownership is still unknown and after the purchase`() =
        runTest {
            repository.adsRemoved.value = null
            val vm = viewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showAds.collect {} }
            assertFalse(vm.showAds.value)

            repository.adsRemoved.value = false
            assertTrue(vm.showAds.value)

            repository.adsRemoved.value = true
            assertFalse(vm.showAds.value)
        }

    @Test
    fun `hides ads while detection is running`() =
        runTest {
            val vm = viewModel()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.showAds.collect {} }

            detector.startDetection()
            detector.isTracking.awaitFirst { it }
            vm.showAds.awaitFirst { !it }

            detector.stopDetection()
            vm.showAds.awaitFirst { it }
        }

    @Test
    fun `remove ads click starts the purchase and thanks once it goes through`() =
        runTest {
            val vm = viewModel()
            vm.uiEffect.test {
                vm.onRemoveAdsClick(activity)
                assertEquals(1, repository.purchaseCalls)

                repository.adsRemoved.value = true
                assertEquals(AdsUiEffect.ShowMessage(R.string.ads_purchase_thanks), awaitItem())
            }
            assertEquals(listOf(AnalyticsEvent.RemoveAdsClicked, AnalyticsEvent.RemoveAdsPurchased), analytics.events)
        }

    @Test
    fun `an ownership restored at startup is not thanked`() =
        runTest {
            val vm = viewModel()
            vm.uiEffect.test {
                repository.adsRemoved.value = true
                expectNoEvents()
            }
            assertTrue(analytics.events.isEmpty())
        }

    @Test
    fun `a store that could not be reached is reported`() =
        runTest {
            val vm = viewModel()
            vm.uiEffect.test {
                repository.purchaseFailures.emit(Unit)
                assertEquals(AdsUiEffect.ShowMessage(R.string.ads_purchase_failed), awaitItem())
            }
            assertEquals(listOf(AnalyticsEvent.RemoveAdsFailed), analytics.events)
        }
}
