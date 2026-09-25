package com.ipirangatech.fidd.core.data

import app.cash.turbine.test
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.UUID

class PreferencesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `onboarding milestones start false and stick once marked`() = runTest {
        val prefs = OnboardingPreferences(testPreferencesDataStore(backgroundScope, tmp.root))

        prefs.hasCompletedOnboarding.test {
            assertFalse(awaitItem())
            prefs.markCompleted()
            assertTrue(awaitItem())
        }
        assertFalse(prefs.hasStartedDetection.first())
        prefs.markDetectionStarted()
        assertTrue(prefs.hasStartedDetection.first())
    }

    @Test
    fun `ranking location is null until saved, then round-trips`() = runTest {
        val prefs = RankingLocationPreferences(testPreferencesDataStore(backgroundScope, tmp.root))

        assertNull(prefs.getLastLocation())
        prefs.saveLastLocation(-23.55, -46.63)
        assertEquals(-23.55 to -46.63, prefs.getLastLocation())
    }

    @Test
    fun `reporter token is a UUID created once and then persisted`() = runTest {
        val store = testPreferencesDataStore(backgroundScope, tmp.root)
        val first = ReporterIdentityProvider(store).getOrCreateToken()

        assertEquals(first, ReporterIdentityProvider(store).getOrCreateToken())
        assertEquals(first, UUID.fromString(first).toString())
    }

    @Test
    fun `different installs get different reporter tokens`() = runTest {
        val a = ReporterIdentityProvider(testPreferencesDataStore(backgroundScope, tmp.newFolder())).getOrCreateToken()
        val b = ReporterIdentityProvider(testPreferencesDataStore(backgroundScope, tmp.newFolder())).getOrCreateToken()

        assertNotEquals(a, b)
    }
}
