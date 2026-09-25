package com.ipirangatech.fidd.feature.tracker.impl

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.model.SensorWindow
import com.ipirangatech.fidd.core.model.SensorWindowSample
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.testPothole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h3000dp")
class DebugScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val repository = FakePotholeRepository()

    private fun str(id: Int, vararg args: Any) = app.getString(id, *args)

    private val window = SensorWindow(
        potholeId = "a",
        samples = listOf(
            SensorWindowSample(-500, 0.5f, 0.2f, 9.8f),
            SensorWindowSample(0, 1f, -2f, 24f),
            SensorWindowSample(400, 0.1f, 0.3f, 8f)
        )
    )

    private val entries = listOf(
        DetectionDebugEntry(
            testPothole(id = "a", timestamp = 3_000, sessionId = "trip-2", serverId = "srv-a", status = "CONFIRMED", distinctReporterCount = 2),
            window,
            DetectionLabel.POTHOLE,
            "buraco fundo"
        ),
        DetectionDebugEntry(testPothole(id = "b", timestamp = 2_000, sessionId = "trip-2"), null, DetectionLabel.UNLABELED, ""),
        DetectionDebugEntry(testPothole(id = "c", timestamp = 1_000, sessionId = "trip-1"), null, DetectionLabel.PHONE_HANDLING, ""),
        DetectionDebugEntry(testPothole(id = "d", timestamp = 500, sessionId = null), null, DetectionLabel.SPEED_BUMP, "")
    )

    @Test
    fun `the list groups detections by trip and filters by label`() {
        repository.debugEntries.value = entries
        val opened = mutableListOf<String>()
        val vm = DebugViewModel(repository)
        compose.setContent { DebugListScreen(viewModel = vm, onOpenDetail = { opened += it }) }

        compose.onNodeWithText(str(R.string.debug_capture_summary_format, 4, 1, 3)).assertExists()
        compose.onNodeWithText(str(R.string.debug_samples_count_format, 3)).assertExists()
        compose.onNodeWithText(str(R.string.debug_session_count_format, 2)).assertExists()

        val speedBumpChip = str(R.string.debug_filter_label_format, DetectionLabel.SPEED_BUMP.displayName, 1)
        compose.onNodeWithText(speedBumpChip).performClick()
        compose.onNodeWithText(str(R.string.debug_session_count_format, 2)).assertDoesNotExist()
        compose.onNodeWithText(speedBumpChip).performClick() // tapping the active filter clears it
        compose.onNodeWithText(str(R.string.debug_session_count_format, 2)).assertExists()

        compose.onNodeWithText(str(R.string.debug_filter_label_format, DetectionLabel.ROUGH_ROAD.displayName, 0)).performClick()
        compose.onNodeWithText(str(R.string.debug_empty_category)).assertExists()
        compose.onNodeWithText(str(R.string.debug_filter_all_format, 4)).performClick()

        compose.onNodeWithText(str(R.string.debug_samples_count_format, 3)).performClick()
        assertEquals(listOf("a"), opened)
    }

    @Test
    fun `list items show a map thumbnail when a key is configured`() {
        setMapsApiKey(app)
        repository.debugEntries.value = entries.take(1)
        val vm = DebugViewModel(repository)
        compose.setContent { DebugListScreen(viewModel = vm, onOpenDetail = {}) }

        compose.onNodeWithText(str(R.string.debug_samples_count_format, 3)).assertExists()
    }

    @Test
    fun `detail shows metadata and the sensor chart, and edits label and note`() {
        setMapsApiKey(app)
        repository.debugEntries.value = entries
        var backPressed = false
        val vm = DebugViewModel(repository)
        compose.setContent { DebugDetailScreen(viewModel = vm, potholeId = "a", onBack = { backPressed = true }) }

        compose.onNodeWithText(str(R.string.debug_trip_number_format, 3)).assertExists() // oldest trip = 1: sessionless (500), trip-1, trip-2
        compose.onNodeWithText(str(R.string.debug_backend_status_format, "CONFIRMED", 2)).assertExists()
        compose.onNodeWithText(str(R.string.debug_severity_format, 24f)).assertExists()
        compose.onNodeWithText(str(R.string.debug_chart_time_min_format, -500)).assertExists()
        compose.onNodeWithText(str(R.string.debug_chart_time_max_format, 400)).assertExists()
        compose.onRoot().captureToImage() // forces the chart to actually draw

        compose.onNodeWithText(DetectionLabel.ROUGH_ROAD.displayName).performClick()
        compose.onNodeWithText("buraco fundo").performTextInput(" perto da lombada")
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        compose.onNodeWithContentDescription(str(R.string.common_back_cd)).performClick()

        assertEquals(listOf("a" to DetectionLabel.ROUGH_ROAD), repository.labelUpdates)
        assertTrue(repository.noteUpdates.single().second.contains("perto da lombada"))
        assertTrue(backPressed)
    }

    @Test
    fun `detail without a captured window says so, and the map opens the spot`() {
        setMapsApiKey(app)
        repository.debugEntries.value = entries
        val vm = DebugViewModel(repository)
        compose.setContent { DebugDetailScreen(viewModel = vm, potholeId = "b", onBack = {}) }

        compose.onNodeWithText(str(R.string.debug_no_sensor_data)).assertExists()
        compose.onNodeWithText(str(R.string.debug_awaiting_lowercase)).assertExists()
        compose.onNodeWithText(str(R.string.history_pending_sync)).assertExists()

        compose.onNodeWithContentDescription(str(R.string.debug_map_location_cd)).performClick()
        assertEquals(Intent.ACTION_VIEW, shadowOf(app).nextStartedActivity.action)
    }

    @Test
    fun `detail map explains when no maps app is installed`() {
        setMapsApiKey(app)
        shadowOf(app).checkActivities(true)
        repository.debugEntries.value = entries
        val vm = DebugViewModel(repository)
        compose.setContent { DebugDetailScreen(viewModel = vm, potholeId = "c", onBack = {}) }

        compose.onNodeWithContentDescription(str(R.string.debug_map_location_cd)).performClick()

        assertEquals(str(R.string.common_no_maps_app_found), ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun `detail for a deleted detection says it is gone`() {
        val vm = DebugViewModel(repository)
        compose.setContent { DebugDetailScreen(viewModel = vm, potholeId = "missing", onBack = {}) }

        compose.onNodeWithText(str(R.string.debug_detection_not_found)).assertExists()
    }
}
