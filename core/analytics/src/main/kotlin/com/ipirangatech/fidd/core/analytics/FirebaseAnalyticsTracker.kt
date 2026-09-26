package com.ipirangatech.fidd.core.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import javax.inject.Inject

/**
 * Manda os [AnalyticsEvent] para o Firebase Analytics. Nunca chama `setUserId` nem
 * `setUserProperty`: o usuário é só a instância anônima do app (app instance id), e a coleta de
 * advertising ID/SSAID e os sinais de anúncio estão desligados no AndroidManifest deste módulo.
 */
class FirebaseAnalyticsTracker
    @Inject
    constructor(
        private val firebaseAnalytics: FirebaseAnalytics,
    ) : AnalyticsTracker {
        override fun log(event: AnalyticsEvent) {
            firebaseAnalytics.logEvent(event.name, event.params.toBundle())
        }

        private fun Map<String, Any>.toBundle(): Bundle =
            Bundle().apply {
                forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key, value)
                        is Long -> putLong(key, value)
                        else -> error("Unsupported analytics param type for '$key': ${value::class}")
                    }
                }
            }
    }
