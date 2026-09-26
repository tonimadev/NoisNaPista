package com.ipirangatech.fidd

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.ipirangatech.fidd.core.analytics.AnalyticsEvent
import com.ipirangatech.fidd.core.analytics.AnalyticsModule
import com.ipirangatech.fidd.core.analytics.AnalyticsTracker
import com.ipirangatech.fidd.core.billing.BillingModule
import com.ipirangatech.fidd.core.billing.RemoveAdsRepository
import com.ipirangatech.fidd.core.data.CityRepository
import com.ipirangatech.fidd.core.data.DataModule
import com.ipirangatech.fidd.core.data.OnboardingPreferences
import com.ipirangatech.fidd.core.data.PotholeRepository
import com.ipirangatech.fidd.core.data.PreferencesModule
import com.ipirangatech.fidd.core.location.LocationModule
import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.sensor.MotionSensor
import com.ipirangatech.fidd.core.sensor.SensorModule
import com.ipirangatech.fidd.core.testing.FakeAnalyticsTracker
import com.ipirangatech.fidd.core.testing.FakeCityRepository
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.FakeRemoveAdsRepository
import com.ipirangatech.fidd.core.testing.testPothole
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.file.Files
import javax.inject.Inject
import com.ipirangatech.fidd.core.ads.R as AdsR
import com.ipirangatech.fidd.feature.map.impl.R as MapR
import com.ipirangatech.fidd.feature.onboarding.R as OnboardingR
import com.ipirangatech.fidd.feature.ranking.impl.R as RankingR
import com.ipirangatech.fidd.feature.tracker.impl.R as TrackerR

/**
 * The app shell end to end: real Hilt graph and ViewModels, with the repositories and hardware
 * swapped for fakes so nothing touches the network, GPS or sensors.
 */
@HiltAndroidTest
@UninstallModules(
    DataModule::class,
    PreferencesModule::class,
    LocationModule::class,
    SensorModule::class,
    BillingModule::class,
    AnalyticsModule::class,
)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h2000dp")
@RunWith(RobolectricTestRunner::class)
class MainActivityTest {
    private val hiltRule = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(compose)

    @BindValue
    @JvmField
    val potholeRepository: PotholeRepository =
        FakePotholeRepository().apply {
            debugEntries.value =
                listOf(
                    DetectionDebugEntry(
                        testPothole(id = "debug-1", sessionId = "trip"),
                        null,
                        DetectionLabel.UNLABELED,
                        "",
                    ),
                )
        }

    @BindValue
    @JvmField
    val cityRepository: CityRepository = FakeCityRepository()

    @BindValue
    @JvmField
    val locationProvider: LocationProvider = FakeLocationProvider()

    @BindValue
    @JvmField
    val motionSensor: MotionSensor = FakeMotionSensor()

    private val fakeRemoveAds = FakeRemoveAdsRepository(adsRemoved = false)

    @BindValue
    @JvmField
    val removeAdsRepository: RemoveAdsRepository = fakeRemoveAds

    private val fakeAnalytics = FakeAnalyticsTracker()

    @BindValue
    @JvmField
    val analytics: AnalyticsTracker = fakeAnalytics

    // The real DataStore is a process-wide singleton that would leak onboarding state between tests.
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @BindValue
    @JvmField
    val dataStore: DataStore<Preferences> =
        testPreferencesDataStore(dataStoreScope, Files.createTempDirectory("prefs").toFile())

    @After
    fun tearDown() = dataStoreScope.cancel()

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Before
    fun setUp() = hiltRule.inject()

    private fun str(id: Int) = compose.activity.getString(id)

    private fun waitForText(text: String) =
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }

    // The onboarding may flash for a frame before completeOnboarding()'s write lands.
    private fun eventsAfterOnboarding() =
        fakeAnalytics.events.filter { it != AnalyticsEvent.ScreenView(AnalyticsEvent.Screen.ONBOARDING) }

    private fun completeOnboarding() {
        runBlocking { onboardingPreferences.markCompleted() }
        waitForText(str(R.string.nav_home))
    }

    @Test
    fun `first launch shows onboarding, and finishing it opens Home`() {
        waitForText(str(OnboardingR.string.onboarding_skip))

        compose.onNodeWithText(str(OnboardingR.string.onboarding_skip)).performClick()

        waitForText(str(TrackerR.string.tracker_start_button))
        assertEquals(
            listOf(
                AnalyticsEvent.ScreenView(AnalyticsEvent.Screen.ONBOARDING),
                AnalyticsEvent.OnboardingFinished(skipped = true),
                AnalyticsEvent.ScreenView(AnalyticsEvent.Screen.HOME),
            ),
            fakeAnalytics.events,
        )
    }

    @Test
    fun `Home invites to remove ads, buying hides them`() {
        completeOnboarding()
        waitForText(str(AdsR.string.ads_remove_button))

        compose.onNodeWithText(str(AdsR.string.ads_remove_button)).performClick()
        assertEquals(1, fakeRemoveAds.purchaseCalls)

        fakeRemoveAds.adsRemoved.value = true
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(str(AdsR.string.ads_remove_button))).fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun `bottom navigation reaches every screen`() {
        completeOnboarding()

        compose.onAllNodesWithText(str(R.string.nav_history))[0].performClick()
        waitForText(str(TrackerR.string.history_title))

        compose.onAllNodesWithText(str(R.string.nav_ranking))[0].performClick()
        waitForText(str(RankingR.string.ranking_title))

        compose.onAllNodesWithText(str(R.string.nav_map))[0].performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(androidx.compose.ui.test.hasContentDescription(str(MapR.string.map_recenter_cd)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onAllNodesWithText(str(R.string.nav_home))[0].performClick()
        waitForText(str(TrackerR.string.tracker_start_button))

        assertEquals(
            listOf(
                AnalyticsEvent.Screen.HOME,
                AnalyticsEvent.Screen.HISTORY,
                AnalyticsEvent.Screen.RANKING,
                AnalyticsEvent.Screen.MAP,
                AnalyticsEvent.Screen.HOME,
            ).map { AnalyticsEvent.ScreenView(it) },
            eventsAfterOnboarding(),
        )
    }

    @Test
    fun `the debug screen lists detections and opens their detail`() {
        completeOnboarding()

        compose.onAllNodesWithText(str(R.string.nav_debug))[0].performClick()
        waitForText(str(TrackerR.string.debug_list_title))
        compose.onNodeWithText(str(TrackerR.string.debug_awaiting_capture)).performClick()
        waitForText(str(TrackerR.string.debug_detail_title))

        compose.onNodeWithContentDescription(str(TrackerR.string.common_back_cd)).performClick()
        waitForText(str(TrackerR.string.debug_list_title))
        assertEquals(
            "the debug screens are not app behavior",
            listOf(AnalyticsEvent.ScreenView(AnalyticsEvent.Screen.HOME)),
            eventsAfterOnboarding(),
        )
    }
}

/** Wide screens show Home and the full map side by side instead of switching tabs. */
@HiltAndroidTest
@UninstallModules(
    DataModule::class,
    PreferencesModule::class,
    LocationModule::class,
    SensorModule::class,
    BillingModule::class,
    AnalyticsModule::class,
)
@Config(application = HiltTestApplication::class, qualifiers = "w1280dp-h800dp-night")
@RunWith(RobolectricTestRunner::class)
class MainActivityExpandedTest {
    private val hiltRule = HiltAndroidRule(this)
    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(compose)

    @BindValue
    @JvmField
    val potholeRepository: PotholeRepository = FakePotholeRepository()

    @BindValue
    @JvmField
    val cityRepository: CityRepository = FakeCityRepository()

    @BindValue
    @JvmField
    val locationProvider: LocationProvider = FakeLocationProvider()

    @BindValue
    @JvmField
    val motionSensor: MotionSensor = FakeMotionSensor()

    private val fakeRemoveAds = FakeRemoveAdsRepository(adsRemoved = false)

    @BindValue
    @JvmField
    val removeAdsRepository: RemoveAdsRepository = fakeRemoveAds

    private val fakeAnalytics = FakeAnalyticsTracker()

    @BindValue
    @JvmField
    val analytics: AnalyticsTracker = fakeAnalytics

    // The real DataStore is a process-wide singleton that would leak onboarding state between tests.
    private val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @BindValue
    @JvmField
    val dataStore: DataStore<Preferences> =
        testPreferencesDataStore(dataStoreScope, Files.createTempDirectory("prefs").toFile())

    @After
    fun tearDown() = dataStoreScope.cancel()

    @Inject
    lateinit var onboardingPreferences: OnboardingPreferences

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun `home and map render side by side in dark theme`() {
        runBlocking { onboardingPreferences.markCompleted() }
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText(compose.activity.getString(TrackerR.string.tracker_start_button)))
                .fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithContentDescription(compose.activity.getString(MapR.string.map_recenter_cd)).assertExists()
        compose.onAllNodesWithText(compose.activity.getString(R.string.nav_map))[0].performClick()
        compose.onAllNodesWithText(compose.activity.getString(R.string.nav_home))[0].performClick()
    }
}
