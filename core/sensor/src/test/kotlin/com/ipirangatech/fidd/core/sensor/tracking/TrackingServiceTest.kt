package com.ipirangatech.fidd.core.sensor.tracking

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.ipirangatech.fidd.core.analytics.AnalyticsEvent
import com.ipirangatech.fidd.core.analytics.AnalyticsModule
import com.ipirangatech.fidd.core.analytics.AnalyticsTracker
import com.ipirangatech.fidd.core.location.LocationModule
import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.sensor.MotionSensor
import com.ipirangatech.fidd.core.sensor.R
import com.ipirangatech.fidd.core.sensor.SensorModule
import com.ipirangatech.fidd.core.testing.FakeAnalyticsTracker
import com.ipirangatech.fidd.core.testing.FakeLocationProvider
import com.ipirangatech.fidd.core.testing.FakeMotionSensor
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import javax.inject.Inject

@HiltAndroidTest
@UninstallModules(SensorModule::class, LocationModule::class, AnalyticsModule::class)
@Config(application = HiltTestApplication::class)
@RunWith(RobolectricTestRunner::class)
class TrackingServiceTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @BindValue
    @JvmField
    val motionSensor: MotionSensor = FakeMotionSensor()

    @BindValue
    @JvmField
    val locationProvider: LocationProvider = FakeLocationProvider()

    private val fakeAnalytics = FakeAnalyticsTracker()

    @BindValue
    @JvmField
    val analytics: AnalyticsTracker = fakeAnalytics

    @Inject
    lateinit var detector: PotholeDetector

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun `creating the service goes foreground with an ongoing notification and starts detection`() {
        val service = Robolectric.buildService(TrackingService::class.java).create().get()

        val notification = shadowOf(service).lastForegroundNotification
        assertNotNull(notification)
        assertEquals(context.getString(R.string.tracking_notification_title), shadowOf(notification).contentTitle)
        assertEquals(1, notification.actions.size)
        assertTrue(detector.isTracking.value)
        val channel =
            context.getSystemService(
                NotificationManager::class.java,
            ).getNotificationChannel("tracking_channel")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertEquals(listOf(AnalyticsEvent.DetectionStarted), fakeAnalytics.events)
    }

    @Test
    fun `a regular start is sticky so the OS restarts detection`() {
        val controller = Robolectric.buildService(TrackingService::class.java).create()

        assertEquals(Service.START_STICKY, controller.get().onStartCommand(Intent(), 0, 1))
        assertFalse(shadowOf(controller.get()).isStoppedBySelf)
    }

    @Test
    fun `the notification's stop action stops the service`() {
        val service = Robolectric.buildService(TrackingService::class.java).create().get()
        val stop = Intent(context, TrackingService::class.java).setAction("com.ipirangatech.fidd.action.STOP_TRACKING")

        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(stop, 0, 1))
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertEquals(null, service.onBind(stop))
        service.onDestroy()
        assertEquals(
            AnalyticsEvent.DetectionStopped(AnalyticsEvent.StopSource.NOTIFICATION, durationMinutes = 0),
            fakeAnalytics.events.last(),
        )
    }

    @Test
    fun `destroying the service stops detection`() {
        val controller = Robolectric.buildService(TrackingService::class.java).create()

        ShadowSystemClock.advanceBy(Duration.ofMinutes(12).plusSeconds(40))
        controller.destroy()

        assertFalse(detector.isTracking.value)
        assertEquals(
            AnalyticsEvent.DetectionStopped(AnalyticsEvent.StopSource.APP, durationMinutes = 12),
            fakeAnalytics.events.last(),
        )
    }
}
