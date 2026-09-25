package com.ipirangatech.fidd.feature.tracker.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertTrue
import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class HistoryScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repository = FakePotholeRepository()
    private val location = FakeLocationProvider(currentLocation = testLocation())

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private fun setContent(adBanner: (@androidx.compose.runtime.Composable () -> Unit)? = null) {
        val vm = HistoryViewModel(repository, location)
        compose.setContent { HistoryScreen(viewModel = vm, adBanner = adBanner) }
    }

    @Test
    fun `the ad sits between my detections and the community list`() {
        setContent(adBanner = { Box(Modifier.testTag("ad").fillMaxWidth().height(50.dp)) })

        val ad = compose.onNodeWithTag("ad").fetchSemanticsNode().boundsInRoot
        val mine = compose.onNodeWithText(str(R.string.common_no_pothole_detected)).fetchSemanticsNode().boundsInRoot
        val community = compose.onNodeWithText(str(R.string.history_community_header)).fetchSemanticsNode().boundsInRoot
        assertTrue(mine.bottom <= ad.top && ad.bottom <= community.top)
    }

    @Test
    fun `empty local and community lists show their empty states`() {
        setContent()

        compose.onNodeWithText(str(R.string.history_title)).assertExists()
        compose.onNodeWithText(str(R.string.common_no_pothole_detected)).assertExists()
        compose.onNodeWithText(str(R.string.history_community_empty)).assertExists()
    }

    @Test
    fun `local detections show sync state and act on the repository`() {
        repository.potholes.value = listOf(
            testPothole(id = "synced", serverId = "srv-1"),
            testPothole(id = "pending", timestamp = 1_600_000_000_000L)
        )
        setContent()

        compose.onNodeWithText(str(R.string.history_synced)).assertExists()
        compose.onNodeWithText(str(R.string.history_pending_sync)).assertExists()
        compose.onAllNodesWithContentDescription(str(R.string.history_mark_false_alarm_cd))[0].performClick()
        compose.onAllNodesWithContentDescription(str(R.string.history_delete_cd))[1].performClick()
        compose.waitForIdle()

        assertEquals(listOf("synced"), repository.markedFalseAlarm.map { it.id })
        assertEquals(listOf("pending"), repository.deleted.map { it.id })
    }

    @Test
    fun `a false alarm is struck through and can no longer be flagged`() {
        repository.potholes.value = listOf(testPothole(id = "a", isFalseAlarm = true))
        setContent()

        compose.onNodeWithText(str(R.string.history_false_alarm_label)).assertExists()
        compose.onAllNodesWithContentDescription(str(R.string.history_mark_false_alarm_cd)).assertCountEquals(0)
    }

    @Test
    fun `without a maps key there is no thumbnail`() {
        repository.potholes.value = listOf(testPothole(id = "a"))
        setContent()

        compose.onAllNodesWithContentDescription(str(R.string.history_map_thumbnail_cd)).assertCountEquals(0)
    }

    @Test
    fun `the map thumbnail opens the spot in a maps app`() {
        setMapsApiKey(app)
        repository.potholes.value = listOf(testPothole(id = "a"))
        setContent()

        compose.onNodeWithContentDescription(str(R.string.history_map_thumbnail_cd)).performClick()

        val opened: Intent = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, opened.action)
        assertEquals("geo", opened.data?.scheme)
    }

    @Test
    fun `the map thumbnail explains when no maps app is installed`() {
        setMapsApiKey(app)
        shadowOf(app).checkActivities(true)
        repository.potholes.value = listOf(testPothole(id = "a"))
        setContent()

        compose.onNodeWithContentDescription(str(R.string.history_map_thumbnail_cd)).performClick()

        assertEquals(str(R.string.common_no_maps_app_found), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `community potholes can be voted fixed unless already fixed`() {
        repository.nearbyResult = Result.success(
            listOf(
                testPothole(id = "srv-1", serverId = "srv-1", status = "CONFIRMED", distinctReporterCount = 3),
                testPothole(id = "srv-2", serverId = "srv-2", status = "FIXED"),
                testPothole(id = "srv-3", serverId = "srv-3", status = null)
            )
        )
        repository.fixVoteResult = { Result.success(testPothole(id = it, serverId = it, status = "FIXED")) }
        setContent()

        compose.onNodeWithText(str(R.string.history_community_status_format, "CONFIRMED")).assertExists()
        compose.onNodeWithText(str(R.string.history_report_count_format, 3)).assertExists()
        compose.onNodeWithText(str(R.string.history_community_status_format, "PENDING")).assertExists()
        compose.onAllNodesWithContentDescription(str(R.string.history_vote_fixed_cd)).assertCountEquals(2)

        compose.onAllNodesWithContentDescription(str(R.string.history_vote_fixed_cd))[0].performClick()
        compose.waitForIdle()

        assertEquals(listOf("srv-1"), repository.votedServerIds)
        assertEquals(str(R.string.history_vote_marked_fixed), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `refresh stays reachable after a failed fetch and reports the failure`() {
        repository.nearbyResult = Result.failure(RuntimeException("offline"))
        setContent()

        compose.onNodeWithText(str(R.string.common_refresh)).performClick()
        compose.waitForIdle()

        assertEquals(str(R.string.history_community_refresh_failed), ShadowToast.getTextOfLatestToast())
    }
}
