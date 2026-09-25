package com.ipirangatech.fidd.feature.tracker.impl

import app.cash.turbine.test
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HistoryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePotholeRepository()
    private val locationProvider = FakeLocationProvider(currentLocation = testLocation())

    private fun viewModel() = HistoryViewModel(repository, locationProvider)

    @Test
    fun `local potholes come straight from the repository`() =
        runTest {
            repository.potholes.value = listOf(testPothole(id = "a"), testPothole(id = "b", isFalseAlarm = true))

            viewModel().potholes.test {
                assertEquals(listOf("a", "b"), awaitItem().map { it.id })
            }
        }

    @Test
    fun `nearby community potholes load silently on start`() =
        runTest {
            repository.nearbyResult = Result.success(listOf(testPothole(id = "srv-1", serverId = "srv-1")))

            val vm = viewModel()

            assertEquals(listOf(testLocation()), repository.fetchedNearby)
            assertEquals(listOf("srv-1"), vm.communityPotholes.value.map { it.id })
        }

    @Test
    fun `the automatic first load never shows an error`() =
        runTest {
            repository.nearbyResult = Result.failure(RuntimeException("offline"))
            val vm = viewModel()

            vm.uiEffect.test { expectNoEvents() }
            locationProvider.currentLocation = null
            viewModel().uiEffect.test { expectNoEvents() }
            assertTrue(vm.communityPotholes.value.isEmpty())
        }

    @Test
    fun `a manual refresh reports a failed fetch or a missing location`() =
        runTest {
            val vm = viewModel()
            vm.uiEffect.test {
                repository.nearbyResult = Result.failure(RuntimeException("offline"))
                vm.onIntent(HistoryUiIntent.RefreshCommunity)
                assertEquals(HistoryUiEffect.ShowMessage(R.string.history_community_refresh_failed), awaitItem())

                locationProvider.currentLocation = null
                vm.onIntent(HistoryUiIntent.RefreshCommunity)
                assertEquals(HistoryUiEffect.ShowMessage(R.string.history_community_location_unavailable), awaitItem())
            }
        }

    @Test
    fun `delete and false alarm go to the repository`() =
        runTest {
            val pothole = testPothole(id = "a")
            repository.potholes.value = listOf(pothole)
            val vm = viewModel()

            vm.onIntent(HistoryUiIntent.MarkFalseAlarm(pothole))
            vm.onIntent(HistoryUiIntent.Delete(pothole))

            assertEquals(listOf(pothole), repository.markedFalseAlarm)
            assertEquals(listOf(pothole), repository.deleted)
        }

    @Test
    fun `a vote replaces the community entry and says whether it is now fixed`() =
        runTest {
            val open = testPothole(id = "srv-1", serverId = "srv-1", status = "CONFIRMED")
            val other = testPothole(id = "srv-2", serverId = "srv-2", status = "PENDING")
            repository.nearbyResult = Result.success(listOf(open, other))
            val vm = viewModel()

            vm.uiEffect.test {
                repository.fixVoteResult = { Result.success(open.copy(distinctReporterCount = 2)) }
                vm.onIntent(HistoryUiIntent.VoteFixed(open))
                assertEquals(HistoryUiEffect.ShowMessage(R.string.history_vote_registered), awaitItem())
                assertEquals(2, vm.communityPotholes.value.first().distinctReporterCount)

                repository.fixVoteResult = { Result.success(open.copy(status = "FIXED")) }
                vm.onIntent(HistoryUiIntent.VoteFixed(open))
                assertEquals(HistoryUiEffect.ShowMessage(R.string.history_vote_marked_fixed), awaitItem())
                assertEquals(listOf("FIXED", "PENDING"), vm.communityPotholes.value.map { it.status })

                repository.fixVoteResult = { Result.failure(RuntimeException("offline")) }
                vm.onIntent(HistoryUiIntent.VoteFixed(open))
                assertEquals(HistoryUiEffect.ShowMessage(R.string.history_vote_failed), awaitItem())
            }
            assertEquals(listOf("srv-1", "srv-1", "srv-1"), repository.votedServerIds)
        }

    @Test
    fun `a pothole without a server id cannot be voted on`() =
        runTest {
            viewModel().onIntent(HistoryUiIntent.VoteFixed(testPothole(serverId = null)))

            assertTrue(repository.votedServerIds.isEmpty())
        }
}
