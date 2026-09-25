package com.ipirangatech.fidd.feature.tracker.impl

import app.cash.turbine.test
import com.ipirangatech.fidd.core.model.DetectionDebugEntry
import com.ipirangatech.fidd.core.model.DetectionLabel
import com.ipirangatech.fidd.core.testing.FakePotholeRepository
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.testPothole
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DebugViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakePotholeRepository()

    @Test
    fun `entries mirror the repository and label or note edits are forwarded`() =
        runTest {
            val entry = DetectionDebugEntry(testPothole(id = "a"), null, DetectionLabel.UNLABELED, "")
            repository.debugEntries.value = listOf(entry)
            val vm = DebugViewModel(repository)

            vm.entries.test {
                assertEquals(listOf(entry), awaitItem())
            }
            vm.updateLabel("a", DetectionLabel.POTHOLE)
            vm.updateNote("a", "lombada")

            assertEquals(listOf("a" to DetectionLabel.POTHOLE), repository.labelUpdates)
            assertEquals(listOf("a" to "lombada"), repository.noteUpdates)
        }
}
