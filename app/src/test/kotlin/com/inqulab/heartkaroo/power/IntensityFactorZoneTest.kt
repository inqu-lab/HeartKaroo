package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.karoo.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Test

class IntensityFactorZoneTest {

    @Test
    fun `coggan IF bands map to the expected colours`() {
        assertEquals(ZoneColors.BLUE, intensityFactorZone(0.74f).color) // recovery
        assertEquals(ZoneColors.GREEN, intensityFactorZone(0.75f).color) // endurance
        assertEquals(ZoneColors.AMBER, intensityFactorZone(0.85f).color) // tempo
        assertEquals(ZoneColors.ORANGE, intensityFactorZone(0.95f).color) // threshold
        assertEquals(ZoneColors.RED, intensityFactorZone(1.05f).color) // vo2+
    }

    @Test
    fun `band labels`() {
        assertEquals("RECOVERY", intensityFactorZone(0.5f).label)
        assertEquals("THRESHOLD", intensityFactorZone(1.0f).label)
        assertEquals("VO2+", intensityFactorZone(1.2f).label)
    }
}
