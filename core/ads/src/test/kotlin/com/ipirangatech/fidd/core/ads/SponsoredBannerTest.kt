package com.ipirangatech.fidd.core.ads

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SponsoredBannerTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `shows the ad and the invite, and the button asks to remove ads`() {
        var removeClicks = 0
        composeRule.setContent {
            SponsoredBanner(
                adUnitId = "test-unit",
                onRemoveAds = { removeClicks++ },
                ad = { Box(it.testTag("ad")) },
            )
        }

        composeRule.onNodeWithTag("ad").assertExists()
        composeRule.onNodeWithText(context.getString(R.string.ads_invite)).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(R.string.ads_remove_button)).performClick()

        assertEquals(1, removeClicks)
    }

    @Test
    fun `real AdMob banner composes and is released without crashing`() {
        var show by mutableStateOf(true)
        composeRule.setContent {
            // Modo inspeção pula o loadAd: pedir anúncio de verdade na JVM dispara uma thread do SDK
            // que estoura sem o App ID do manifest. O que importa aqui é criar e liberar o AdView.
            CompositionLocalProvider(LocalInspectionMode provides true) {
                if (show) {
                    AdMobBanner(
                        adUnitId = "ca-app-pub-3940256099942544/9214589741",
                        modifier = Modifier.testTag("banner"),
                    )
                }
            }
        }
        composeRule.onNodeWithTag("banner").assertExists()

        show = false
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("banner").assertDoesNotExist()
    }
}
