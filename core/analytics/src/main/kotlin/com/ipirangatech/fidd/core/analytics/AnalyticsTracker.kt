package com.ipirangatech.fidd.core.analytics

/** Registra o comportamento de uso do app. Implementação real: [FirebaseAnalyticsTracker]. */
interface AnalyticsTracker {
    fun log(event: AnalyticsEvent)
}
