package digital.tonima.noisnapista.core.data

import digital.tonima.noisnapista.core.model.DetectionDebugEntry
import digital.tonima.noisnapista.core.model.DetectionLabel
import digital.tonima.noisnapista.core.model.Pothole
import digital.tonima.noisnapista.core.model.SensorWindow
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

    /** Fetches the community's currently active (non-FIXED) potholes from the backend. Not
     * cached locally — a fresh network call every time, for map/community-list display. */
    suspend fun fetchCommunityPotholes(): Result<List<Pothole>>

    /** Casts this device's "this was fixed" vote for a backend-known pothole (by its [serverId]). */
    suspend fun castFixVote(serverId: String): Result<Pothole>

    /** Debug-only labeling screen source: every local detection paired with its raw sensor
     * window (when already captured) and current manual classification, newest first. */
    fun getDebugEntries(): Flow<List<DetectionDebugEntry>>

    suspend fun updateDetectionLabel(potholeId: String, label: DetectionLabel)
    suspend fun updateDetectionNote(potholeId: String, note: String)
}
