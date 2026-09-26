package com.ipirangatech.fidd.core.testing

import com.ipirangatech.fidd.core.analytics.AnalyticsEvent
import com.ipirangatech.fidd.core.analytics.AnalyticsTracker
import java.util.concurrent.CopyOnWriteArrayList

class FakeAnalyticsTracker : AnalyticsTracker {
    // Thread-safe: o TrackingService e os ViewModels podem registrar de threads diferentes.
    val events: MutableList<AnalyticsEvent> = CopyOnWriteArrayList()

    override fun log(event: AnalyticsEvent) {
        events += event
    }
}
