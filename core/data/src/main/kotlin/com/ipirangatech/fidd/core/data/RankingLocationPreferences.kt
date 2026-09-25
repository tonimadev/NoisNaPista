package com.ipirangatech.fidd.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The last location the user explicitly shared to find "minha cidade" on the ranking screen.
 * Persisted so revisiting the screen shows their city straight away — without a new GPS fix or
 * permission prompt every time; a fresh fix is only taken when the user asks to update it.
 */
@Singleton
class RankingLocationPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val latitudeKey = doublePreferencesKey("ranking_last_latitude")
    private val longitudeKey = doublePreferencesKey("ranking_last_longitude")

    suspend fun getLastLocation(): Pair<Double, Double>? {
        val prefs = dataStore.data.first()
        val lat = prefs[latitudeKey] ?: return null
        val lon = prefs[longitudeKey] ?: return null
        return lat to lon
    }

    suspend fun saveLastLocation(latitude: Double, longitude: Double) {
        dataStore.edit {
            it[latitudeKey] = latitude
            it[longitudeKey] = longitude
        }
    }
}
