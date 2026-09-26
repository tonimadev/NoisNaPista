package com.ipirangatech.fidd.feature.onboarding

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val app: Application = ApplicationProvider.getApplicationContext()

    // Um item por término; true = saiu pelo "Pular".
    private val finishes = mutableListOf<Boolean>()

    private fun str(id: Int) = app.getString(id)

    @Test
    fun `next walks through every page and the last button finishes`() {
        compose.setContent { OnboardingScreen(onFinish = { finishes += it }) }

        compose.onNodeWithText(str(R.string.onboarding_page1_title)).assertExists()
        compose.onNodeWithText(str(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(str(R.string.onboarding_page2_title)).assertExists()
        compose.onNodeWithText(str(R.string.onboarding_next)).performClick()
        compose.onNodeWithText(str(R.string.onboarding_page3_title)).assertExists()
        assertEquals(emptyList<Boolean>(), finishes)

        compose.onNodeWithText(str(R.string.onboarding_start)).performClick()
        assertEquals(listOf(false), finishes)
    }

    @Test
    fun `skip finishes right away`() {
        compose.setContent { OnboardingScreen(onFinish = { finishes += it }) }

        compose.onNodeWithText(str(R.string.onboarding_skip)).performClick()

        assertEquals(listOf(true), finishes)
    }
}
