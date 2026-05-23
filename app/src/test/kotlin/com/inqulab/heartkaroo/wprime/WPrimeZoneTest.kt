package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.karoo.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Test

class WPrimeZoneTest {

    private val max = 20_000f

    @Test
    fun `percentage of W-prime remaining picks the band`() {
        assertEquals(ZoneColors.GREEN, wPrimeZone(0.80f * max, max).color)
        assertEquals(ZoneColors.GREEN, wPrimeZone(0.66f * max, max).color)
        assertEquals(ZoneColors.AMBER, wPrimeZone(0.50f * max, max).color)
        assertEquals(ZoneColors.AMBER, wPrimeZone(0.33f * max, max).color)
        assertEquals(ZoneColors.RED, wPrimeZone(0.10f * max, max).color)
    }

    @Test
    fun `values are clamped and a non-positive max reads neutral`() {
        assertEquals(ZoneColors.GREEN, wPrimeZone(2f * max, max).color) // over-full clamps to 100%
        assertEquals(ZoneColors.RED, wPrimeZone(-100f, max).color) // negative clamps to 0%
        assertEquals(ZoneColors.NEUTRAL, wPrimeZone(5_000f, 0f).color)
    }
}
