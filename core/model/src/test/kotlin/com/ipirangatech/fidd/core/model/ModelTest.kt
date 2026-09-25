package com.ipirangatech.fidd.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetectionLabelTest {
    @Test
    fun `stored names map back to their label`() {
        DetectionLabel.entries.forEach { label ->
            assertEquals(label, DetectionLabel.fromStorageValueOrDefault(label.name))
        }
    }

    @Test
    fun `missing or unknown stored values fall back to UNLABELED`() {
        assertEquals(DetectionLabel.UNLABELED, DetectionLabel.fromStorageValueOrDefault(null))
        assertEquals(DetectionLabel.UNLABELED, DetectionLabel.fromStorageValueOrDefault("NOT_A_LABEL"))
    }

    @Test
    fun `every label has a user-facing name`() {
        DetectionLabel.entries.forEach { assert(it.displayName.isNotBlank()) }
    }
}

class DetectionDebugEntryTest {
    private val pothole =
        Pothole(
            id = "p1",
            location = LocationPoint(-23.5, -46.6, 5f, 0L),
            severity = 20f,
            timestamp = 0L,
        )

    @Test
    fun `peakAbsZ is the largest absolute Z across the whole window`() {
        val window =
            SensorWindow(
                potholeId = "p1",
                samples =
                    listOf(
                        SensorWindowSample(offsetMs = -10, x = 0f, y = 0f, z = 9.8f),
                        SensorWindowSample(offsetMs = 0, x = 0f, y = 0f, z = -31f),
                        SensorWindowSample(offsetMs = 10, x = 50f, y = 0f, z = 25f),
                    ),
            )

        assertEquals(31f, DetectionDebugEntry(pothole, window, DetectionLabel.UNLABELED, "").peakAbsZ)
    }

    @Test
    fun `peakAbsZ is null until the window is captured, or when it is empty`() {
        assertNull(DetectionDebugEntry(pothole, null, DetectionLabel.UNLABELED, "").peakAbsZ)
        assertNull(DetectionDebugEntry(pothole, SensorWindow("p1", emptyList()), DetectionLabel.UNLABELED, "").peakAbsZ)
    }
}

/** The @Serializable models are persisted (sensor windows) and passed around as JSON; pin the shape. */
class ModelSerializationTest {
    private val json = Json

    @Test
    fun `pothole round-trips through JSON with its optional fields`() {
        val pothole =
            Pothole(
                id = "p1",
                location = LocationPoint(-23.5, -46.6, 5f, 1L, speed = 12f),
                severity = 20f,
                timestamp = 1L,
                isFalseAlarm = true,
                serverId = "s1",
                status = "CONFIRMED",
                distinctReporterCount = 3,
                sessionId = "trip",
            )

        assertEquals(pothole, json.decodeFromString<Pothole>(json.encodeToString(pothole)))
    }

    @Test
    fun `defaults are applied when optional fields are absent`() {
        val decoded =
            json.decodeFromString<Pothole>(
                """{"id":"p1","location":{"latitude":1.0,"longitude":2.0,"accuracy":3.0,"timestamp":4},""" +
                    """"severity":5.0,"timestamp":6}""",
            )

        assertEquals(false, decoded.isFalseAlarm)
        assertNull(decoded.serverId)
        assertNull(decoded.location.speed)
        assertEquals(1, decoded.distinctReporterCount)
    }

    @Test
    fun `sensor windows, city rankings and sensor data round-trip`() {
        val window = SensorWindow("p1", listOf(SensorWindowSample(-5, 1f, 2f, 3f)))
        val ranking =
            CityRankingList(
                totalCities = 1,
                top = listOf(CityRanking(1, "A", "SP", 3, 1, 0, rank = 1)),
                bottom = listOf(CityRanking(2, "B", "RJ", 1, 0, 0, rank = null)),
            )
        val sensorData = SensorData(1f, 2f, 3f, 4L)

        assertEquals(window, json.decodeFromString<SensorWindow>(json.encodeToString(window)))
        assertEquals(ranking, json.decodeFromString<CityRankingList>(json.encodeToString(ranking)))
        assertEquals(sensorData, json.decodeFromString<SensorData>(json.encodeToString(sensorData)))
    }
}
