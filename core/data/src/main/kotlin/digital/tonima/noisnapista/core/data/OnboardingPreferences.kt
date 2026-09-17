package digital.tonima.noisnapista.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Tracks whether the user has been through the one-time onboarding shown on first launch. */
@Singleton
class OnboardingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val completedKey = booleanPreferencesKey("onboarding_completed")

    val hasCompletedOnboarding: Flow<Boolean> = dataStore.data.map { it[completedKey] ?: false }

    suspend fun markCompleted() {
        dataStore.edit { it[completedKey] = true }
    }
}
