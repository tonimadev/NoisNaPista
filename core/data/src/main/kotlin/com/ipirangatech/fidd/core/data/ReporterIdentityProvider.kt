package com.ipirangatech.fidd.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app's single anonymous reporter identity: a random UUID generated once on first use
 * and persisted locally, sent as the `X-Reporter-Token` header on every write call to the
 * backend. It carries no personal information — the backend only ever sees/stores a one-way hash
 * of it — but it's what lets the server tell "the same device reported this" for delete
 * ownership and distinct-reporter corroboration counts.
 */
@Singleton
class ReporterIdentityProvider @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val reporterTokenKey = stringPreferencesKey("reporter_token")

    suspend fun getOrCreateToken(): String {
        val existing = dataStore.data.first()[reporterTokenKey]
        if (existing != null) return existing

        val newToken = UUID.randomUUID().toString()
        dataStore.edit { it[reporterTokenKey] = newToken }
        return newToken
    }
}
