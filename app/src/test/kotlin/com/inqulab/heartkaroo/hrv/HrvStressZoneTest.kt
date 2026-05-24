package com.inqulab.heartkaroo.hrv

import com.inqulab.heartkaroo.karoo.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Test

class HrvStressZoneTest {

    @Test
    fun `stress bands map to the expected colours`() {
        assertEquals(ZoneColors.GREEN, hrvStressZone(0f).color)
        assertEquals(ZoneColors.GREEN, hrvStressZone(32.9f).color)
        assertEquals(ZoneColors.AMBER, hrvStressZone(33f).color)
        assertEquals(ZoneColors.AMBER, hrvStressZone(65.9f).color)
        assertEquals(ZoneColors.RED, hrvStressZone(66f).color)
        assertEquals(ZoneColors.RED, hrvStressZone(100f).color)
    }
}
