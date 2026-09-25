package com.ipirangatech.fidd.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DiModulesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `database module hands out the DAOs of the provided database`() {
        val db = DataModule.provideDatabase(context)
        try {
            assertSame(db.potholeDao(), DataModule.providePotholeDao(db))
            assertSame(db.sensorWindowDao(), DataModule.provideSensorWindowDao(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun `the preferences DataStore is a per-process singleton`() {
        assertSame(PreferencesModule.provideDataStore(context), PreferencesModule.provideDataStore(context))
    }
}
