package com.ipirangatech.fidd.core.ads

import android.content.Context
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MobileAdsInitializerTest {
    private val context: Context = mockk()
    private val scope = TestScope(UnconfinedTestDispatcher())

    @Test
    fun `initializes the SDK with the given context`() {
        var initializedWith: Context? = null

        initializeMobileAds(context, scope) { initializedWith = it }

        assertSame(context, initializedWith)
    }

    @Test
    fun `a failing initialization does not crash the app`() {
        // Sem WebView o MobileAds lança; se isso escapasse, o app cairia a cada abertura.
        initializeMobileAds(context, scope) { throw IllegalStateException("no WebView") }
    }
}
