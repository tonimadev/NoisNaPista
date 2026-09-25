package com.ipirangatech.fidd

import androidx.hilt.work.HiltWorkerFactory
import io.mockk.mockk
import org.junit.Assert.assertSame
import org.junit.Test

class FiddAppTest {

    @Test
    fun `WorkManager is configured with the Hilt worker factory`() {
        // Without this, @HiltWorker SyncWorker can't be constructed (see the manifest comment).
        val factory = mockk<HiltWorkerFactory>()
        val app = FiddApp().apply { workerFactory = factory }

        assertSame(factory, app.workManagerConfiguration.workerFactory)
    }
}
