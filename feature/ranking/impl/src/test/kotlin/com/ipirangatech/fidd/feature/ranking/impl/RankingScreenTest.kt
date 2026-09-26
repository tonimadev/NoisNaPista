package com.ipirangatech.fidd.feature.ranking.impl

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.data.RankingLocationPreferences
import com.ipirangatech.fidd.core.model.CityRankingList
import com.ipirangatech.fidd.core.model.CityRankingSortBy
import com.ipirangatech.fidd.core.testing.FakeAnalyticsTracker
import com.ipirangatech.fidd.core.testing.FakeCityRepository
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.testCityRanking
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h3000dp")
class RankingScreenTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val tmp = TemporaryFolder()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val cities = FakeCityRepository()
    private val location = FakeLocationProvider()
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val preferences by lazy { RankingLocationPreferences(testPreferencesDataStore(dataStoreScope, tmp.root)) }

    private val saoPaulo =
        testCityRanking(ibgeCode = 1, name = "São Paulo", totalPotholes = 40, fixedPotholes = 5, rank = 1)
    private val campinas =
        testCityRanking(ibgeCode = 2, name = "Campinas", totalPotholes = 20, fixedPotholes = 9, rank = 2)
    private val santos = testCityRanking(ibgeCode = 3, name = "Santos", totalPotholes = 1, fixedPotholes = 0, rank = 3)
    private val list = CityRankingList(totalCities = 3, top = listOf(saoPaulo, campinas), bottom = listOf(santos))

    @After
    fun tearDown() = dataStoreScope.cancel()

    private fun str(
        id: Int,
        vararg args: Any,
    ) = app.getString(id, *args)

    private fun waitForText(text: String) =
        compose.waitUntil(3_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    private fun setContent(
        dark: Boolean = false,
        adBanner: (@androidx.compose.runtime.Composable () -> Unit)? = null,
    ): RankingViewModel {
        val vm = RankingViewModel(cities, location, preferences, FakeAnalyticsTracker())
        compose.setContent {
            if (dark) {
                MaterialTheme(colorScheme = darkColorScheme()) { RankingScreen(viewModel = vm, adBanner = adBanner) }
            } else {
                RankingScreen(viewModel = vm, adBanner = adBanner)
            }
        }
        return vm
    }

    @Test
    fun `the ranking shows best and worst cities for the chosen metric`() {
        cities.rankingResult = { Result.success(list) }
        setContent()

        compose.onNodeWithText(app.resources.getQuantityString(R.plurals.ranking_subtitle_format, 3, 3)).assertExists()
        compose.onNodeWithText(
            str(R.string.ranking_top_header_format, str(R.string.ranking_metric_potholes_lower)),
        ).assertExists()
        compose.onNodeWithText(
            str(R.string.ranking_bottom_header_format, str(R.string.ranking_metric_potholes_lower)),
        ).assertExists()
        compose.onNodeWithText("Campinas").assertExists()
        compose.onNodeWithText(str(R.string.ranking_my_city_prompt)).assertExists()

        compose.onNodeWithText(str(R.string.ranking_metric_fixed)).performClick()
        waitForText(str(R.string.ranking_top_header_format, str(R.string.ranking_metric_fixed_lower)))
        assertEquals(listOf(CityRankingSortBy.POTHOLES, CityRankingSortBy.FIXED), cities.rankingRequests)
    }

    @Test
    fun `the dark theme ranking renders with a recurrence metric`() {
        cities.rankingResult = { Result.success(list.copy(bottom = emptyList())) }
        setContent(dark = true)

        compose.onNodeWithText(str(R.string.ranking_metric_recurrence)).performClick()

        waitForText(str(R.string.ranking_top_header_format, str(R.string.ranking_metric_recurrence_lower)))
        compose.onNodeWithText(
            str(R.string.ranking_bottom_header_format, str(R.string.ranking_metric_recurrence_lower)),
        ).assertDoesNotExist()
    }

    @Test
    fun `the ad sits between the top and bottom sections`() {
        cities.rankingResult = { Result.success(list) }
        setContent(adBanner = { Box(Modifier.testTag("ad").fillMaxWidth().height(50.dp)) })

        val ad = compose.onNodeWithTag("ad").fetchSemanticsNode().boundsInRoot
        val topRow = compose.onNodeWithText("Campinas").fetchSemanticsNode().boundsInRoot
        val bottomHeader =
            compose.onNodeWithText(
                str(R.string.ranking_bottom_header_format, str(R.string.ranking_metric_potholes_lower)),
            ).fetchSemanticsNode().boundsInRoot
        assertTrue(topRow.bottom <= ad.top && ad.bottom <= bottomHeader.top)
    }

    @Test
    @Config(qualifiers = "w411dp-h700dp")
    fun `with an ad, jumping to my city in the bottom section lands on its row`() {
        // O banner é um item a mais antes da seção de baixo; sem contá-lo, o pulo parava uma
        // linha antes (no cabeçalho), e numa lista maior a cidade podia nem aparecer.
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        runBlocking { preferences.saveLastLocation(-23.5, -46.6) }
        location.currentLocation = testLocation()
        // Linhas depois de Santos, para a lista ter como rolar até deixá-lo no topo.
        val below = (10..19).map { testCityRanking(ibgeCode = it, name = "Cidade $it", totalPotholes = 0, rank = it) }
        cities.rankingResult = { Result.success(list.copy(bottom = listOf(santos) + below)) }
        cities.nearestResult = { _, _, _ -> Result.success(santos) }
        setContent(adBanner = { Box(Modifier.testTag("ad").fillMaxWidth().height(50.dp)) })
        waitForText(str(R.string.ranking_view_in_list_button))

        compose.onNodeWithText(str(R.string.ranking_view_in_list_button)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(
            str(R.string.ranking_bottom_header_format, str(R.string.ranking_metric_potholes_lower)),
        ).assertIsNotDisplayed()
        compose.onNodeWithTag("ad").assertIsNotDisplayed()
    }

    @Test
    fun `an empty ranking explains itself`() {
        setContent()

        compose.onNodeWithText(str(R.string.ranking_empty_title)).assertExists()
    }

    @Test
    fun `a failed first load offers a retry that recovers`() {
        cities.rankingResult = { Result.failure(RuntimeException("offline")) }
        setContent()
        compose.onNodeWithText(str(R.string.ranking_error_title)).assertExists()
        compose.onNodeWithText(str(R.string.ranking_my_city_prompt)).assertDoesNotExist()

        cities.rankingResult = { Result.success(list) }
        compose.onNodeWithText(str(R.string.ranking_retry_button)).performClick()

        waitForText("São Paulo")
    }

    @Test
    fun `a spinner shows while the first load is in flight`() {
        val response = CompletableDeferred<Result<CityRankingList>>()
        cities.rankingResult = { response.await() }
        setContent()

        compose.onNodeWithText(str(R.string.ranking_empty_title)).assertDoesNotExist()
        compose.onNodeWithText(str(R.string.ranking_error_title)).assertDoesNotExist()
        response.complete(Result.success(list))
        waitForText("São Paulo")
    }

    @Test
    fun `finding my city asks for location first, then shows its rank and jumps to its row`() {
        cities.rankingResult = { Result.success(list) }
        location.currentLocation = testLocation()
        cities.nearestResult = { _, _, _ -> Result.success(santos) }
        setContent()

        compose.onNodeWithText(str(R.string.ranking_view_my_city_button)).performClick()
        val request = shadowOf(compose.activity).lastRequestedPermission
        assertEquals(Manifest.permission.ACCESS_FINE_LOCATION, request.requestedPermissions.single())
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        @Suppress("DEPRECATION")
        compose.activity.onRequestPermissionsResult(
            request.requestCode,
            request.requestedPermissions,
            intArrayOf(PackageManager.PERMISSION_GRANTED),
        )

        waitForText(app.resources.getQuantityString(R.plurals.ranking_of_total_format, 3, 3))
        compose.onNodeWithText("SP · ${str(R.string.ranking_your_city_tag)}").assertExists()
        compose.onNodeWithText(str(R.string.ranking_view_in_list_button)).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `with permission granted my city refreshes in place and can jump to a top row`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        runBlocking { preferences.saveLastLocation(-23.5, -46.6) }
        cities.rankingResult = { Result.success(list) }
        location.currentLocation = testLocation()
        cities.nearestResult = { _, _, _ -> Result.success(saoPaulo) }
        setContent()
        waitForText(str(R.string.ranking_view_in_list_button))

        compose.onNodeWithText(str(R.string.ranking_view_in_list_button)).performClick()
        compose.onNodeWithContentDescription(str(R.string.ranking_refresh_button)).performClick()

        compose.waitUntil(3_000) { location.currentLocationRequests == 1 }
    }

    @Test
    fun `a city with no potholes for the metric shows as unranked, and locating shows progress`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        cities.rankingResult = { Result.success(list) }
        location.currentLocation = testLocation()
        val response = CompletableDeferred<Result<com.ipirangatech.fidd.core.model.CityRanking>>()
        cities.nearestResult = { _, _, _ -> response.await() }
        setContent()

        compose.onNodeWithText(str(R.string.ranking_view_my_city_button)).performClick()
        waitForText(str(R.string.ranking_locating))
        response.complete(Result.success(testCityRanking(ibgeCode = 99, name = "Ilhabela", rank = null)))

        waitForText(str(R.string.ranking_unranked))
        compose.onNodeWithText(str(R.string.ranking_no_potholes_metric)).assertExists()
        compose.onAllNodesWithText(str(R.string.ranking_view_in_list_button)).fetchSemanticsNodes().let {
            assertEquals(0, it.size)
        }
    }

    @Test
    fun `failures are reported as a toast`() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        setContent()

        compose.onNodeWithText(str(R.string.ranking_view_my_city_button)).performClick()

        compose.waitUntil(3_000) { ShadowToast.getTextOfLatestToast() == str(R.string.ranking_location_failed) }
    }
}
