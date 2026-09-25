package com.ipirangatech.fidd.core.data

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.model.GeoBounds
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.model.SensorWindow
import com.ipirangatech.fidd.core.model.SensorWindowSample
import com.ipirangatech.fidd.core.network.PotholeReadingBatchRequestDto
import com.ipirangatech.fidd.core.network.PotholeReadingRequestDto
import com.ipirangatech.fidd.core.network.PotholeResponseDto
import com.ipirangatech.fidd.core.network.PotholeService
import dagger.hilt.android.qualifiers.ApplicationContext
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
class PotholeRepositoryImpl
    @Inject
    constructor(
        private val potholeDao: PotholeDao,
        private val sensorWindowDao: SensorWindowDao,
        private val potholeService: PotholeService,
        private val reporterIdentityProvider: ReporterIdentityProvider,
        @ApplicationContext private val context: Context,
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
            var failedChunks = 0
            // One request per chunk instead of one per pothole — a long drive's worth of detections
            // flushes in a handful of round trips. Idempotent by clientId, so resending is harmless.
            unsynced.chunked(PotholeReadingBatchRequestDto.MAX_SIZE).forEach { chunk ->
                try {
                    val results =
                        potholeService.submitReadings(
                            reporterToken,
                            PotholeReadingBatchRequestDto(chunk.map { it.toRequestDto() }),
                        )
                    results.forEach { result ->
                        val pothole = result.pothole
                        if (pothole != null) {
                            potholeDao.markAsSynced(result.clientId, pothole.id, pothole.status)
                        } else {
                            // Rejected on its own merits (e.g. bad timestamp): retrying right away
                            // won't change the answer, so it doesn't trigger a WorkManager retry — it
                            // stays unsynced and is resent with the next sync.
                            Log.w(TAG, "Backend rejected pothole ${result.clientId}: ${result.error}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync ${chunk.size} pothole(s)", e)
                    failedChunks++
                }
            }
            // SyncWorker relies on this to decide Result.success() vs Result.retry() — without it,
            // a totally offline sync attempt would silently report "success" and WorkManager would
            // never retry.
            if (failedChunks > 0) {
                throw IOException("$failedChunks sync request(s) failed for ${unsynced.size} pothole(s)")
            }
        }

        override suspend fun saveSensorWindow(window: SensorWindow) {
            sensorWindowDao.insert(
                SensorWindowEntity(
                    potholeId = window.potholeId,
                    samplesJson = json.encodeToString(window.samples),
                    capturedAt = System.currentTimeMillis(),
                ),
            )
        }

        override suspend fun fetchCommunityPotholes(bounds: GeoBounds): Result<List<Pothole>> =
            runCatching {
                potholeService.getPotholes(
                    minLat = bounds.minLatitude,
                    minLon = bounds.minLongitude,
                    maxLat = bounds.maxLatitude,
                    maxLon = bounds.maxLongitude,
                ).map { it.toExternalModel() }
            }

        override suspend fun fetchNearbyCommunityPotholes(location: LocationPoint): Result<List<Pothole>> =
            runCatching {
                potholeService.getNearbyPotholes(location.latitude, location.longitude).map { it.toExternalModel() }
            }

        override suspend fun castFixVote(serverId: String): Result<Pothole> =
            runCatching {
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
                        sensorWindow =
                            windowEntity?.let {
                                SensorWindow(
                                    it.potholeId,
                                    json.decodeFromString<List<SensorWindowSample>>(it.samplesJson),
                                )
                            },
                        label = DetectionLabel.fromStorageValueOrDefault(windowEntity?.label),
                        note = windowEntity?.note.orEmpty(),
                    )
                }
            }

        override suspend fun updateDetectionLabel(
            potholeId: String,
            label: DetectionLabel,
        ) {
            sensorWindowDao.updateLabel(potholeId, label.name)
        }

        override suspend fun updateDetectionNote(
            potholeId: String,
            note: String,
        ) {
            sensorWindowDao.updateNote(potholeId, note)
        }

        private companion object {
            const val TAG = "PotholeRepository"
        }

        private fun scheduleSync() {
            val constraints =
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

            val syncRequest =
                OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "PotholeSync",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                syncRequest,
            )
        }
    }

fun PotholeEntity.toExternalModel() =
    Pothole(
        id = id,
        location = LocationPoint(latitude, longitude, accuracy, timestamp),
        severity = severity,
        timestamp = timestamp,
        isFalseAlarm = isFalseAlarm,
        serverId = serverId,
        status = status,
        sessionId = sessionId,
    )

fun Pothole.toEntity() =
    PotholeEntity(
        id = id,
        latitude = location.latitude,
        longitude = location.longitude,
        accuracy = location.accuracy,
        severity = severity,
        timestamp = timestamp,
        isFalseAlarm = isFalseAlarm,
        serverId = serverId,
        status = status,
        sessionId = sessionId,
    )

fun PotholeEntity.toRequestDto() =
    PotholeReadingRequestDto(
        clientId = id,
        latitude = latitude,
        longitude = longitude,
        severity = severity,
        timestamp = timestamp,
    )

/** A community-fetched pothole has no local id of its own — the backend's id serves as both. */
fun PotholeResponseDto.toExternalModel() =
    Pothole(
        id = id,
        location =
            LocationPoint(
                latitude,
                longitude,
                accuracy = 0f,
                timestamp = Instant.parse(createdAt).toEpochMilli(),
            ),
        severity = severity,
        timestamp = Instant.parse(createdAt).toEpochMilli(),
        serverId = id,
        status = status,
        distinctReporterCount = distinctReporterCount,
    )
