package digital.tonima.noisnapista.core.data

import android.content.Context
import android.util.Log
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.tonima.noisnapista.core.model.DetectionDebugEntry
import digital.tonima.noisnapista.core.model.DetectionLabel
import digital.tonima.noisnapista.core.model.LocationPoint
import digital.tonima.noisnapista.core.model.Pothole
import digital.tonima.noisnapista.core.model.SensorWindow
import digital.tonima.noisnapista.core.model.SensorWindowSample
import digital.tonima.noisnapista.core.network.PotholeReadingRequestDto
import digital.tonima.noisnapista.core.network.PotholeResponseDto
import digital.tonima.noisnapista.core.network.PotholeService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PotholeRepositoryImpl @Inject constructor(
    private val potholeDao: PotholeDao,
    private val sensorWindowDao: SensorWindowDao,
    private val potholeService: PotholeService,
    private val reporterIdentityProvider: ReporterIdentityProvider,
    @ApplicationContext private val context: Context
) : PotholeRepository {

    private val json = Json { ignoreUnknownKeys = true }

    override fun getPotholes(): Flow<List<Pothole>> =
        potholeDao.getAllPotholes().map { entities ->
            entities.map { it.toExternalModel() }
        }

    override fun getActivePotholes(): Flow<List<Pothole>> =
        potholeDao.getActivePotholes().map { entities ->
            entities.map { it.toExternalModel() }
        }

    override suspend fun savePothole(pothole: Pothole) {
        potholeDao.insertPothole(pothole.toEntity())
        scheduleSync()
    }

    override suspend fun delete(pothole: Pothole) {
        pothole.serverId?.let { serverId ->
            try {
                potholeService.deletePothole(serverId, reporterIdentityProvider.getOrCreateToken())
            } catch (e: Exception) {
                // Best-effort: still remove it locally even if the backend call fails (offline,
                // already deleted server-side, etc.) — the user's intent is "get rid of this".
            }
        }
        potholeDao.delete(pothole.toEntity())
    }

    override suspend fun markFalseAlarm(pothole: Pothole) {
        potholeDao.markAsFalseAlarm(pothole.id)
    }

    override suspend fun syncPotholes() {
        val unsynced = potholeDao.getUnsyncedPotholes()
        if (unsynced.isEmpty()) return
        val reporterToken = reporterIdentityProvider.getOrCreateToken()
        var failureCount = 0
        unsynced.forEach { entity ->
            try {
                val response = potholeService.submitReading(reporterToken, entity.toRequestDto())
                potholeDao.markAsSynced(entity.id, response.id, response.status)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync pothole ${entity.id}", e)
                failureCount++
            }
        }
        // SyncWorker relies on this to decide Result.success() vs Result.retry() — without it,
        // a totally offline sync attempt would silently report "success" and WorkManager would
        // never retry.
        if (failureCount > 0) {
            throw IOException("$failureCount of ${unsynced.size} pothole(s) failed to sync")
        }
    }

    override suspend fun saveSensorWindow(window: SensorWindow) {
        sensorWindowDao.insert(
            SensorWindowEntity(
                potholeId = window.potholeId,
                samplesJson = json.encodeToString(window.samples),
                capturedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun fetchCommunityPotholes(): Result<List<Pothole>> =
        runCatching { potholeService.getPotholes().map { it.toExternalModel() } }

    override suspend fun castFixVote(serverId: String): Result<Pothole> = runCatching {
        val reporterToken = reporterIdentityProvider.getOrCreateToken()
        potholeService.castFixVote(serverId, reporterToken).toExternalModel()
    }

    override fun getDebugEntries(): Flow<List<DetectionDebugEntry>> =
        combine(potholeDao.getAllPotholes(), sensorWindowDao.getAllWindows()) { potholeEntities, windowEntities ->
            val windowsByPotholeId = windowEntities.associateBy { it.potholeId }
            potholeEntities.map { potholeEntity ->
                val windowEntity = windowsByPotholeId[potholeEntity.id]
                DetectionDebugEntry(
                    pothole = potholeEntity.toExternalModel(),
                    sensorWindow = windowEntity?.let {
                        SensorWindow(it.potholeId, json.decodeFromString<List<SensorWindowSample>>(it.samplesJson))
                    },
                    label = DetectionLabel.fromStorageValueOrDefault(windowEntity?.label),
                    note = windowEntity?.note.orEmpty()
                )
            }
        }

    override suspend fun updateDetectionLabel(potholeId: String, label: DetectionLabel) {
        sensorWindowDao.updateLabel(potholeId, label.name)
    }

    override suspend fun updateDetectionNote(potholeId: String, note: String) {
        sensorWindowDao.updateNote(potholeId, note)
    }

    private companion object {
        const val TAG = "PotholeRepository"
    }

    private fun scheduleSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "PotholeSync",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            syncRequest
        )
    }
}

fun PotholeEntity.toExternalModel() = Pothole(
    id = id,
    location = LocationPoint(latitude, longitude, accuracy, timestamp),
    severity = severity,
    timestamp = timestamp,
    isFalseAlarm = isFalseAlarm,
    serverId = serverId,
    status = status,
    sessionId = sessionId
)

fun Pothole.toEntity() = PotholeEntity(
    id = id,
    latitude = location.latitude,
    longitude = location.longitude,
    accuracy = location.accuracy,
    severity = severity,
    timestamp = timestamp,
    isFalseAlarm = isFalseAlarm,
    serverId = serverId,
    status = status,
    sessionId = sessionId
)

fun PotholeEntity.toRequestDto() = PotholeReadingRequestDto(
    clientId = id,
    latitude = latitude,
    longitude = longitude,
    severity = severity,
    timestamp = timestamp
)

/** A community-fetched pothole has no local id of its own — the backend's id serves as both. */
fun PotholeResponseDto.toExternalModel() = Pothole(
    id = id,
    location = LocationPoint(latitude, longitude, accuracy = 0f, timestamp = Instant.parse(createdAt).toEpochMilli()),
    severity = severity,
    timestamp = Instant.parse(createdAt).toEpochMilli(),
    serverId = id,
    status = status,
    distinctReporterCount = distinctReporterCount
)
