package com.ipirangatech.fidd.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.model.GeoBounds
import com.ipirangatech.fidd.core.model.SensorWindow
import com.ipirangatech.fidd.core.model.SensorWindowSample
import com.ipirangatech.fidd.core.network.NetworkModule
import com.ipirangatech.fidd.core.network.REPORTER_TOKEN_HEADER
import com.ipirangatech.fidd.core.testing.testLocation
import com.ipirangatech.fidd.core.testing.testPothole
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.IOException
import java.time.Instant

/** Real Room (in-memory) + real Retrofit against a fake server: the full local→backend path. */
@RunWith(RobolectricTestRunner::class)
class PotholeRepositoryImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val server = MockWebServer()
    private lateinit var db: AppDatabase

    private fun potholeJson(id: String, status: String = "PENDING") =
        """{"id":"$id","latitude":-23.5,"longitude":-46.6,"severity":18.0,"status":"$status","readingCount":1,"distinctReporterCount":2,"createdAt":"2026-09-01T12:00:00Z"}"""

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        server.start()
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun TestScope.repository(): PotholeRepositoryImpl {
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(NetworkModule.provideOkHttpClient())
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
        return PotholeRepositoryImpl(
            potholeDao = db.potholeDao(),
            sensorWindowDao = db.sensorWindowDao(),
            potholeService = NetworkModule.providePotholeService(retrofit),
            reporterIdentityProvider = ReporterIdentityProvider(testPreferencesDataStore(backgroundScope, tmp.root)),
            context = context
        )
    }

    private fun enqueue(code: Int, body: String = "") {
        server.enqueue(MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build())
    }

    @Test
    fun `saving a pothole stores it locally and schedules a sync`() = runTest {
        val repo = repository()

        repo.savePothole(testPothole(id = "a", sessionId = "trip-1"))

        val stored = repo.getPotholes().first().single()
        assertEquals("a", stored.id)
        assertEquals("trip-1", stored.sessionId)
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("PotholeSync").get()
        assertEquals(WorkInfo.State.ENQUEUED, work.single().state)
    }

    @Test
    fun `false alarms are hidden from the active list but kept in history`() = runTest {
        val repo = repository()
        repo.savePothole(testPothole(id = "a", timestamp = 1))
        repo.savePothole(testPothole(id = "b", timestamp = 2))

        repo.markFalseAlarm(testPothole(id = "a"))

        assertEquals(listOf("b"), repo.getActivePotholes().first().map { it.id })
        assertEquals(listOf("b", "a"), repo.getPotholes().first().map { it.id })
        assertTrue(repo.getPotholes().first().first { it.id == "a" }.isFalseAlarm)
    }

    @Test
    fun `deleting an unsynced pothole never calls the backend`() = runTest {
        val repo = repository()
        val pothole = testPothole(id = "a")
        repo.savePothole(pothole)

        repo.delete(pothole)

        assertTrue(repo.getPotholes().first().isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `deleting a synced pothole deletes it on the backend too, and locally even if that fails`() = runTest {
        val repo = repository()
        val synced = testPothole(id = "a", serverId = "srv-a")
        val other = testPothole(id = "b", serverId = "srv-b")
        repo.savePothole(synced)
        repo.savePothole(other)
        enqueue(204)
        enqueue(500)

        repo.delete(synced)
        repo.delete(other)

        val first = server.takeRequest()
        assertEquals("DELETE", first.method)
        assertEquals("/api/v1/potholes/srv-a", first.url.encodedPath)
        assertTrue(first.headers[REPORTER_TOKEN_HEADER]!!.isNotBlank())
        assertTrue(repo.getPotholes().first().isEmpty())
    }

    @Test
    fun `sync with nothing pending makes no request`() = runTest {
        repository().syncPotholes()

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `sync marks accepted readings as synced and leaves rejected ones pending`() = runTest {
        val repo = repository()
        repo.savePothole(testPothole(id = "ok"))
        repo.savePothole(testPothole(id = "bad"))
        enqueue(200, """[{"clientId":"ok","pothole":${potholeJson("srv-ok", "CONFIRMED")}},{"clientId":"bad","error":"timestamp is too old"}]""")

        repo.syncPotholes()

        val request = server.takeRequest()
        assertEquals("/api/v1/potholes/batch", request.url.encodedPath)
        val byId = repo.getPotholes().first().associateBy { it.id }
        assertEquals("srv-ok", byId.getValue("ok").serverId)
        assertEquals("CONFIRMED", byId.getValue("ok").status)
        assertNull(byId.getValue("bad").serverId)
        assertEquals(listOf("bad"), db.potholeDao().getUnsyncedPotholes().map { it.id })
    }

    @Test
    fun `sync splits large backlogs into batches of MAX_SIZE`() = runTest {
        val repo = repository()
        repeat(101) { repo.savePothole(testPothole(id = "p$it")) }
        enqueue(200, "[]")
        enqueue(200, "[]")

        repo.syncPotholes()

        assertEquals(2, server.requestCount)
    }

    @Test(expected = IOException::class)
    fun `sync throws when a batch request fails so WorkManager retries`() = runTest {
        val repo = repository()
        repo.savePothole(testPothole(id = "a"))
        enqueue(503)

        repo.syncPotholes()
    }

    @Test
    fun `community fetches map backend DTOs to potholes keyed by server id`() = runTest {
        val repo = repository()
        enqueue(200, "[${potholeJson("srv-1")}]")
        enqueue(200, "[${potholeJson("srv-2")}]")

        val inViewport = repo.fetchCommunityPotholes(GeoBounds(-24.0, -47.0, -23.0, -46.0)).getOrThrow().single()
        val nearby = repo.fetchNearbyCommunityPotholes(testLocation()).getOrThrow().single()

        assertEquals("srv-1", inViewport.id)
        assertEquals("srv-1", inViewport.serverId)
        assertEquals(2, inViewport.distinctReporterCount)
        assertEquals(Instant.parse("2026-09-01T12:00:00Z").toEpochMilli(), inViewport.timestamp)
        assertEquals("srv-2", nearby.id)
        assertEquals("/api/v1/potholes", server.takeRequest().url.encodedPath)
        assertEquals("/api/v1/potholes/nearby", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `network failures surface as failed results, not exceptions`() = runTest {
        val repo = repository()
        enqueue(500)
        enqueue(500)
        enqueue(500)

        assertTrue(repo.fetchCommunityPotholes(GeoBounds(0.0, 0.0, 1.0, 1.0)).isFailure)
        assertTrue(repo.fetchNearbyCommunityPotholes(testLocation()).isFailure)
        assertTrue(repo.castFixVote("srv-1").isFailure)
    }

    @Test
    fun `fix vote returns the updated pothole`() = runTest {
        val repo = repository()
        enqueue(200, potholeJson("srv-1", "FIXED"))

        val updated = repo.castFixVote("srv-1").getOrThrow()

        assertEquals("FIXED", updated.status)
        assertEquals("/api/v1/potholes/srv-1/fix-votes", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `the reporter token is generated once and reused across calls`() = runTest {
        val repo = repository()
        enqueue(200, potholeJson("srv-1"))
        enqueue(200, potholeJson("srv-1"))

        repo.castFixVote("srv-1")
        repo.castFixVote("srv-1")

        val first = server.takeRequest().headers[REPORTER_TOKEN_HEADER]
        val second = server.takeRequest().headers[REPORTER_TOKEN_HEADER]
        assertEquals(first, second)
    }

    @Test
    fun `debug entries pair each detection with its sensor window, label and note`() = runTest {
        val repo = repository()
        repo.savePothole(testPothole(id = "with-window", timestamp = 2))
        repo.savePothole(testPothole(id = "without-window", timestamp = 1))
        val samples = listOf(SensorWindowSample(-10, 0f, 0f, 9.8f), SensorWindowSample(0, 0f, 0f, 22f))
        repo.saveSensorWindow(SensorWindow("with-window", samples))

        repo.getDebugEntries().test {
            val initial = awaitItem()
            assertEquals(listOf("with-window", "without-window"), initial.map { it.pothole.id })
            assertEquals(samples, initial[0].sensorWindow!!.samples)
            assertEquals(DetectionLabel.UNLABELED, initial[0].label)
            assertNull(initial[1].sensorWindow)
            assertEquals("", initial[1].note)

            repo.updateDetectionLabel("with-window", DetectionLabel.SPEED_BUMP)
            assertEquals(DetectionLabel.SPEED_BUMP, awaitItem()[0].label)

            repo.updateDetectionNote("with-window", "celular no suporte")
            assertEquals("celular no suporte", awaitItem()[0].note)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, db.sensorWindowDao().count())
        assertFalse(db.sensorWindowDao().getAllWindows().first().single().samplesJson.isBlank())
    }
}
