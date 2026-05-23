package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.karoo.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Test

class QuadrantZoneTest {

    @Test
    fun `each quadrant maps to its colour and label`() {
        assertEquals(ZoneColors.RED, quadrantZone(1f).color)
        assertEquals("THRESHOLD", quadrantZone(1f).label)
        assertEquals(ZoneColors.ORANGE, quadrantZone(2f).color)
        assertEquals("GRIND", quadrantZone(2f).label)
        assertEquals(ZoneColors.GREEN, quadrantZone(3f).color)
        assertEquals("EASY", quadrantZone(3f).label)
        assertEquals(ZoneColors.BLUE, quadrantZone(4f).color)
        assertEquals("SPIN", quadrantZone(4f).label)
    }

    @Test
    fun `out-of-range reads neutral`() {
        assertEquals(ZoneColors.NEUTRAL, quadrantZone(0f).color)
        assertEquals(ZoneColors.NEUTRAL, quadrantZone(5f).color)
    }
}
