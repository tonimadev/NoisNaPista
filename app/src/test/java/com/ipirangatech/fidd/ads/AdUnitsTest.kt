package com.ipirangatech.fidd.ads

import org.junit.Assert.assertEquals
import org.junit.Test

class AdUnitsTest {
    @Test
    fun `debug builds always use the Google test banner`() {
        AdPlacement.entries.forEach { assertEquals(AdUnits.TEST_BANNER, AdUnits.banner(it, debug = true)) }
    }

    @Test
    fun `release builds use each placement's configured banner`() {
        AdPlacement.entries.forEach { assertEquals(it.releaseUnitId, AdUnits.banner(it, debug = false)) }
    }
}
