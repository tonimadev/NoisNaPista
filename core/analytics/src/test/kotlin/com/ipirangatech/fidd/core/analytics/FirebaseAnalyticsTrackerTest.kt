package com.ipirangatech.fidd.core.analytics

import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FirebaseAnalyticsTrackerTest {
    private val firebase = mockk<FirebaseAnalytics>(relaxed = true)
    private val tracker = FirebaseAnalyticsTracker(firebase)

    private fun logged(event: AnalyticsEvent): Bundle {
        val bundle = slot<Bundle>()
        tracker.log(event)
        verify { firebase.logEvent(event.name, capture(bundle)) }
        return bundle.captured
    }

    @Test
    fun `screen views use Firebase's own event and parameter names`() {
        val bundle = logged(AnalyticsEvent.ScreenView(AnalyticsEvent.Screen.RANKING))

        verify { firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, any()) }
        assertEquals("ranking", bundle.getString(FirebaseAnalytics.Param.SCREEN_NAME))
    }

    @Test
    fun `numbers and flags go as longs, enums as lowercase strings`() {
        val stopped = logged(AnalyticsEvent.DetectionStopped(AnalyticsEvent.StopSource.NOTIFICATION, 25))
        assertEquals("notification", stopped.getString("source"))
        assertEquals(25L, stopped.getLong("duration_min"))

        assertEquals(1L, logged(AnalyticsEvent.OnboardingFinished(skipped = true)).getLong("skipped"))
    }

    @Test
    fun `events without parameters send an empty bundle`() {
        assertTrue(logged(AnalyticsEvent.PotholeDetected).isEmpty)
    }

    @Test
    fun `the module hands out the app's FirebaseAnalytics instance`() {
        mockkStatic(FirebaseAnalytics::class)
        try {
            every { FirebaseAnalytics.getInstance(any()) } returns firebase
            assertSame(firebase, AnalyticsModule.provideFirebaseAnalytics(ApplicationProvider.getApplicationContext()))
        } finally {
            unmockkStatic(FirebaseAnalytics::class)
        }
    }
}
