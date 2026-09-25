package com.ipirangatech.fidd.core.testing

import com.ipirangatech.fidd.core.data.PotholeRepository
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.model.GeoBounds
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.model.SensorWindow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory [PotholeRepository]: local potholes live in [potholes], network calls return the
 * configurable `*Result` fields, and every call is recorded for assertions. */
class FakePotholeRepository : PotholeRepository {
    val potholes = MutableStateFlow<List<Pothole>>(emptyList())
    val debugEntries = MutableStateFlow<List<DetectionDebugEntry>>(emptyList())

    val savedSensorWindows = mutableListOf<SensorWindow>()
    val deleted = mutableListOf<Pothole>()
    val markedFalseAlarm = mutableListOf<Pothole>()
    val fetchedBounds = mutableListOf<GeoBounds>()
    val fetchedNearby = mutableListOf<LocationPoint>()
    val votedServerIds = mutableListOf<String>()
    val labelUpdates = mutableListOf<Pair<String, DetectionLabel>>()
    val noteUpdates = mutableListOf<Pair<String, String>>()
    var syncCount = 0

    var communityResult: Result<List<Pothole>> = Result.success(emptyList())
    var nearbyResult: Result<List<Pothole>> = Result.success(emptyList())
    var fixVoteResult: (String) -> Result<Pothole> = { Result.failure(IllegalStateException("not stubbed")) }

    override fun getPotholes(): Flow<List<Pothole>> = potholes

    override fun getActivePotholes(): Flow<List<Pothole>> = potholes.map { list -> list.filterNot { it.isFalseAlarm } }

    override suspend fun savePothole(pothole: Pothole) {
        potholes.value = listOf(pothole) + potholes.value.filterNot { it.id == pothole.id }
    }

    override suspend fun delete(pothole: Pothole) {
        deleted += pothole
        potholes.value = potholes.value.filterNot { it.id == pothole.id }
    }

    override suspend fun markFalseAlarm(pothole: Pothole) {
        markedFalseAlarm += pothole
        potholes.value = potholes.value.map { if (it.id == pothole.id) it.copy(isFalseAlarm = true) else it }
    }

    override suspend fun syncPotholes() {
        syncCount++
    }

    override suspend fun saveSensorWindow(window: SensorWindow) {
        savedSensorWindows += window
    }

    override suspend fun fetchCommunityPotholes(bounds: GeoBounds): Result<List<Pothole>> {
        fetchedBounds += bounds
        return communityResult
    }

    override suspend fun fetchNearbyCommunityPotholes(location: LocationPoint): Result<List<Pothole>> {
        fetchedNearby += location
        return nearbyResult
    }

    override suspend fun castFixVote(serverId: String): Result<Pothole> {
        votedServerIds += serverId
        return fixVoteResult(serverId)
    }

    override fun getDebugEntries(): Flow<List<DetectionDebugEntry>> = debugEntries

    override suspend fun updateDetectionLabel(potholeId: String, label: DetectionLabel) {
        labelUpdates += potholeId to label
    }

    override suspend fun updateDetectionNote(potholeId: String, note: String) {
        noteUpdates += potholeId to note
    }
}
