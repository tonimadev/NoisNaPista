package com.ipirangatech.fidd.feature.ranking.impl

import app.cash.turbine.test
import com.ipirangatech.fidd.core.data.RankingLocationPreferences
import com.ipirangatech.fidd.core.model.CityRankingList
import com.ipirangatech.fidd.core.model.CityRankingSortBy
import com.ipirangatech.fidd.core.testing.FakeCityRepository
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.awaitFirst
import com.ipirangatech.fidd.core.testing.testCityRanking
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RankingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val cities = FakeCityRepository()
    private val location = FakeLocationProvider()
    private val list = CityRankingList(
        totalCities = 3,
        top = listOf(testCityRanking(ibgeCode = 1, rank = 1)),
        bottom = listOf(testCityRanking(ibgeCode = 3, rank = 3))
    )

    private fun TestScope.preferences() = RankingLocationPreferences(testPreferencesDataStore(backgroundScope, tmp.root))

    private fun TestScope.viewModel(preferences: RankingLocationPreferences = preferences()) =
        RankingViewModel(cities, location, preferences)

    @Test
    fun `the ranking loads on open`() = runTest {
        cities.rankingResult = { Result.success(list) }

        val state = viewModel().uiState.value

        assertEquals(3, state.totalCities)
        assertEquals(list.top, state.top)
        assertEquals(list.bottom, state.bottom)
        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertNull(state.myCity)
    }

    @Test
    fun `a failed first load shows the retry state without a message`() = runTest {
        cities.rankingResult = { Result.failure(RuntimeException("offline")) }
        val vm = viewModel()

        vm.uiEffect.test { expectNoEvents() }
        assertTrue(vm.uiState.value.loadFailed)
    }

    @Test
    fun `a failed refresh keeps the cached ranking and says so`() = runTest {
        cities.rankingResult = { Result.success(list) }
        val vm = viewModel()
        cities.rankingResult = { Result.failure(RuntimeException("offline")) }

        vm.uiEffect.test {
            vm.refresh()
            assertEquals(RankingUiEffect.ShowMessage(R.string.ranking_load_failed), awaitItem())
        }
        assertFalse(vm.uiState.value.loadFailed)
        assertEquals(list.top, vm.uiState.value.top)
    }

    @Test
    fun `changing the metric refetches, and choosing the same one does nothing`() = runTest {
        val vm = viewModel()

        vm.onSortByChanged(CityRankingSortBy.POTHOLES)
        vm.onSortByChanged(CityRankingSortBy.FIXED)

        assertEquals(listOf(CityRankingSortBy.POTHOLES, CityRankingSortBy.FIXED), cities.rankingRequests)
        assertEquals(CityRankingSortBy.FIXED, vm.uiState.value.sortBy)
        assertTrue("no saved location, so no city lookup", cities.nearestRequests.isEmpty())
    }

    @Test
    fun `finding my city saves the fix and shows its row`() = runTest {
        val prefs = preferences()
        location.currentLocation = testLocation(latitude = -23.0, longitude = -46.0)
        cities.nearestResult = { _, _, _ -> Result.success(testCityRanking(rank = 7)) }
        val vm = viewModel(prefs)

        vm.findMyCity()

        assertEquals(7, vm.uiState.awaitFirst { it.myCity != null && !it.isLocatingMyCity }.myCity?.rank)
        assertFalse(vm.uiState.value.isLocatingMyCity)
        assertEquals(-23.0 to -46.0, prefs.getLastLocation())
    }

    @Test
    fun `finding my city without a fix or a resolvable city explains why`() = runTest {
        val vm = viewModel()

        vm.uiEffect.test {
            vm.findMyCity()
            assertEquals(RankingUiEffect.ShowMessage(R.string.ranking_location_failed), awaitItem())

            location.currentLocation = testLocation()
            cities.nearestResult = { _, _, _ -> Result.failure(RuntimeException("no city")) }
            vm.findMyCity()
            assertEquals(RankingUiEffect.ShowMessage(R.string.ranking_city_resolve_failed), awaitItem())
        }
        assertFalse(vm.uiState.value.isLocatingMyCity)
    }

    @Test
    fun `a saved location restores my city on open and follows metric changes without a new fix`() = runTest {
        val prefs = preferences()
        prefs.saveLastLocation(-23.0, -46.0)
        cities.nearestResult = { _, _, sortBy -> Result.success(testCityRanking(rank = sortBy.ordinal + 1)) }
        val vm = viewModel(prefs)

        assertEquals(1, vm.uiState.awaitFirst { it.myCity != null }.myCity?.rank)
        vm.onSortByChanged(CityRankingSortBy.RECURRENCE)
        vm.refresh()

        assertEquals(3, vm.uiState.value.myCity?.rank)
        assertEquals(0, location.currentLocationRequests)
        assertEquals(
            listOf(CityRankingSortBy.POTHOLES, CityRankingSortBy.RECURRENCE, CityRankingSortBy.RECURRENCE),
            cities.nearestRequests.map { it.third }
        )
    }

    @Test
    fun `a failed silent city refresh keeps the last known row`() = runTest {
        val prefs = preferences()
        prefs.saveLastLocation(-23.0, -46.0)
        cities.nearestResult = { _, _, _ -> Result.success(testCityRanking(rank = 1)) }
        val vm = viewModel(prefs)
        vm.uiState.awaitFirst { it.myCity != null }
        cities.nearestResult = { _, _, _ -> Result.failure(RuntimeException("offline")) }

        vm.uiEffect.test {
            vm.refresh()
            expectNoEvents()
        }
        assertEquals(1, vm.uiState.value.myCity?.rank)
    }
}
