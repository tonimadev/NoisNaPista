package digital.tonima.noisnapista.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks the user's first-run milestones: whether they've been through the one-time onboarding
 * shown on first launch, and whether they've ever turned detection on (the Home screen keeps a
 * "how it works" hint up until they do).
 */
@Singleton
class OnboardingPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val completedKey = booleanPreferencesKey("onboarding_completed")
    private val detectionStartedKey = booleanPreferencesKey("detection_started_once")

    val hasCompletedOnboarding: Flow<Boolean> = dataStore.data.map { it[completedKey] ?: false }

    suspend fun markCompleted() {
        dataStore.edit { it[completedKey] = true }
    }

    val hasStartedDetection: Flow<Boolean> = dataStore.data.map { it[detectionStartedKey] ?: false }

    suspend fun markDetectionStarted() {
        dataStore.edit { it[detectionStartedKey] = true }
    }
}
