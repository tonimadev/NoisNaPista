package com.ipirangatech.fidd.feature.onboarding

import app.cash.turbine.test
import com.ipirangatech.fidd.core.data.OnboardingPreferences
import com.ipirangatech.fidd.core.testing.MainDispatcherRule
import com.ipirangatech.fidd.core.testing.testPreferencesDataStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OnboardingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `state is unknown until read, then false, then true once finished`() =
        runTest {
            val vm = OnboardingViewModel(OnboardingPreferences(testPreferencesDataStore(backgroundScope, tmp.root)))

            vm.hasCompletedOnboarding.test {
                var state = awaitItem()
                if (state == null) state = awaitItem()
                assertEquals(false, state)

                vm.onOnboardingFinished()
                assertEquals(true, awaitItem())
            }
        }
}
