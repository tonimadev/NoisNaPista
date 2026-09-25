package com.ipirangatech.fidd.core.data

import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.model.GeoBounds
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.model.SensorWindow
import kotlinx.coroutines.flow.Flow

interface PotholeRepository {
    fun getPotholes(): Flow<List<Pothole>>
    fun getActivePotholes(): Flow<List<Pothole>>
    suspend fun savePothole(pothole: Pothole)
    suspend fun delete(pothole: Pothole)
    suspend fun markFalseAlarm(pothole: Pothole)
    suspend fun syncPotholes()

    /** Persists a raw sensor burst captured around a detection — local only, for later offline
     * labeling / model training. Never synced to the backend. */
    suspend fun saveSensorWindow(window: SensorWindow)

    /** Fetches the community's active (non-FIXED) potholes inside [bounds] — the map viewport.
     * Server-capped, so a very zoomed-out viewport returns the most corroborated ones only. Not
     * cached locally — a fresh network call every time. */
    suspend fun fetchCommunityPotholes(bounds: GeoBounds): Result<List<Pothole>>

    /** Fetches the community's active potholes around [location], nearest first — for the
     * community list. Not cached locally. */
    suspend fun fetchNearbyCommunityPotholes(location: LocationPoint): Result<List<Pothole>>

    /** Casts this device's "this was fixed" vote for a backend-known pothole (by its [serverId]). */
    suspend fun castFixVote(serverId: String): Result<Pothole>

    /** Debug-only labeling screen source: every local detection paired with its raw sensor
     * window (when already captured) and current manual classification, newest first. */
    fun getDebugEntries(): Flow<List<DetectionDebugEntry>>

    suspend fun updateDetectionLabel(potholeId: String, label: DetectionLabel)
    suspend fun updateDetectionNote(potholeId: String, note: String)
}
