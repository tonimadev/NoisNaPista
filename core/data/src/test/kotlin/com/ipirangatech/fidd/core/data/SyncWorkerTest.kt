package com.ipirangatech.fidd.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SyncWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun worker(repository: PotholeRepository): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SyncWorker(appContext, workerParameters, repository)
                },
            )
            .build()

    @Test
    fun `a successful sync reports success`() =
        runTest {
            val repository = FakePotholeRepository()

            assertEquals(ListenableWorker.Result.success(), worker(repository).doWork())
            assertEquals(1, repository.syncCount)
        }

    @Test
    fun `a failed sync asks WorkManager to retry`() =
        runTest {
            val failing =
                object : PotholeRepository by FakePotholeRepository() {
                    override suspend fun syncPotholes() = throw java.io.IOException("offline")
                }

            assertEquals(ListenableWorker.Result.retry(), worker(failing).doWork())
        }
}
