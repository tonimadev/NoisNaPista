package com.ipirangatech.fidd.core.billing

import android.app.Activity
import app.cash.turbine.test
import com.ipirangatech.fidd.core.billing.RemoveAdsRepository.Companion.REMOVE_ADS_PRODUCT_ID
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayRemoveAdsRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val payWall = FakePayWallManager()
    private val activity: Activity = mockk()

    @Test
    fun `ownership is unknown until the store answers`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = PlayRemoveAdsRepository(payWall)
            runCurrent()
            assertNull(repository.adsRemoved.value)

            payWall.isReady.value = true
            runCurrent()
            assertEquals(false, repository.adsRemoved.value)
        }

    @Test
    fun `a store that never answers is treated as not owned after the grace period`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // Billing indisponível (Play sem conta): sem isso o app nunca mostraria anúncio.
            val repository = PlayRemoveAdsRepository(payWall)
            advanceTimeBy(4_999)
            runCurrent()
            assertNull(repository.adsRemoved.value)

            advanceTimeBy(2)
            runCurrent()
            assertEquals(false, repository.adsRemoved.value)

            payWall.ownedProductIds.value = setOf(REMOVE_ADS_PRODUCT_ID)
            runCurrent()
            assertEquals(true, repository.adsRemoved.value)
        }

    @Test
    fun `owning remove_ads_premium removes ads`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = PlayRemoveAdsRepository(payWall)
            payWall.isReady.value = true
            runCurrent()
            assertEquals(false, repository.adsRemoved.value)

            payWall.ownedProductIds.value = setOf(REMOVE_ADS_PRODUCT_ID)
            runCurrent()

            assertEquals(true, repository.adsRemoved.value)
        }

    @Test
    fun `already owned product is reflected before the first collection`() =
        runTest(mainDispatcherRule.testDispatcher) {
            payWall.ownedProductIds.value = setOf(REMOVE_ADS_PRODUCT_ID)

            assertEquals(true, PlayRemoveAdsRepository(payWall).adsRemoved.value)
        }

    @Test
    fun `other products do not remove ads and losing ownership brings them back`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = PlayRemoveAdsRepository(payWall)
            payWall.isReady.value = true
            payWall.ownedProductIds.value = setOf("something_else")
            runCurrent()
            assertEquals(false, repository.adsRemoved.value)

            payWall.ownedProductIds.value = setOf(REMOVE_ADS_PRODUCT_ID)
            runCurrent()
            payWall.ownedProductIds.value = emptySet()
            runCurrent()
            assertEquals(false, repository.adsRemoved.value)
        }

    @Test
    fun `connect delegates to PayWall`() {
        PlayRemoveAdsRepository(payWall).connect()
        assertEquals(1, payWall.connectCalls)
    }

    @Test
    fun `purchase when ready launches the flow right away`() =
        runTest(mainDispatcherRule.testDispatcher) {
            payWall.isReady.value = true

            PlayRemoveAdsRepository(payWall).purchase(activity)

            assertEquals(listOf(REMOVE_ADS_PRODUCT_ID), payWall.purchases)
            assertEquals(0, payWall.connectCalls)
        }

    @Test
    fun `purchase before ready reconnects and launches once ready`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = PlayRemoveAdsRepository(payWall)

            repository.purchase(activity)
            runCurrent()
            assertEquals(1, payWall.connectCalls)
            assertTrue(payWall.purchases.isEmpty())

            payWall.isReady.value = true
            runCurrent()
            assertEquals(listOf(REMOVE_ADS_PRODUCT_ID), payWall.purchases)
        }

    @Test
    fun `purchase reports a failure when the store never becomes ready`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val repository = PlayRemoveAdsRepository(payWall)

            repository.purchaseFailures.test {
                repository.purchase(activity)
                advanceTimeBy(15_001)
                awaitItem()
            }
            assertTrue(payWall.purchases.isEmpty())
        }
}
