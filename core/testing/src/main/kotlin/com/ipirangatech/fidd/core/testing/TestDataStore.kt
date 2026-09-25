package com.ipirangatech.fidd.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

/** A real file-backed Preferences DataStore in [directory] (use a JUnit TemporaryFolder). */
fun testPreferencesDataStore(
    scope: CoroutineScope,
    directory: File,
): DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "test.preferences_pb") }
