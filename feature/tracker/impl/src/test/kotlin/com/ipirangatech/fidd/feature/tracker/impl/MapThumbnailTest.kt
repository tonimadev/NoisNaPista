package com.ipirangatech.fidd.feature.tracker.impl

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MapThumbnailTest {

    private val app: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `the maps key is read from the manifest meta-data`() {
        assertNull(getMapsApiKey(app))

        setMapsApiKey(app, "abc")

        assertEquals("abc", getMapsApiKey(app))
    }

    @Test
    fun `static map urls center on the point, optionally marked`() {
        val marked = staticMapUrl("k", -23.5, -46.6)
        val unmarked = staticMapUrl("k", -23.5, -46.6, zoom = 15, withMarker = false)

        assertTrue(marked.startsWith("https://maps.googleapis.com/maps/api/staticmap?center=-23.5,-46.6&zoom=17"))
        assertTrue(marked.contains("&markers=color:red%7C-23.5,-46.6"))
        assertTrue(marked.endsWith("&key=k"))
        assertTrue(unmarked.contains("zoom=15"))
        assertFalse(unmarked.contains("markers="))
    }
}
