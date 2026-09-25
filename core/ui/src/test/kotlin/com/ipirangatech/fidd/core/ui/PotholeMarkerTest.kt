package com.ipirangatech.fidd.core.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PotholeMarkerTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `severity maps to light, medium and strong colors at the 10 and 20 thresholds`() {
        assertEquals(PotholeMarker.COLOR_LIGHT, PotholeMarker.severityColor(10f))
        assertEquals(PotholeMarker.COLOR_MEDIUM, PotholeMarker.severityColor(10.1f))
        assertEquals(PotholeMarker.COLOR_MEDIUM, PotholeMarker.severityColor(20f))
        assertEquals(PotholeMarker.COLOR_STRONG, PotholeMarker.severityColor(20.1f))
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    fun `bitmap is a square sized in dp and cached per color and size`() {
        val bitmap = PotholeMarker.bitmap(context, PotholeMarker.COLOR_STRONG)

        assertEquals(90, bitmap.width) // 30dp at 3x
        assertEquals(90, bitmap.height)
        assertSame(bitmap, PotholeMarker.bitmap(context, PotholeMarker.COLOR_STRONG))
        assertNotSame(bitmap, PotholeMarker.bitmap(context, PotholeMarker.COLOR_COMMUNITY))
        assertEquals(60, PotholeMarker.bitmap(context, PotholeMarker.COLOR_STRONG, sizeDp = 20f).width)
    }
}
