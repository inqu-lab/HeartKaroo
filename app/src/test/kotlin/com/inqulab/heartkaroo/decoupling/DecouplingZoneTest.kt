package com.inqulab.heartkaroo.decoupling

import com.inqulab.heartkaroo.karoo.ZoneColors
import org.junit.Assert.assertEquals
import org.junit.Test

class DecouplingZoneTest {

    @Test
    fun `under five percent (incl negatives) is coupled`() {
        assertEquals(ZoneColors.GREEN, decouplingZone(-2f).color)
        assertEquals(ZoneColors.GREEN, decouplingZone(4.9f).color)
        assertEquals("COUPLED", decouplingZone(0f).label)
    }

    @Test
    fun `five to ten percent is drift`() {
        assertEquals(ZoneColors.AMBER, decouplingZone(5f).color)
        assertEquals(ZoneColors.AMBER, decouplingZone(9.9f).color)
    }

    @Test
    fun `ten percent and over is decoupled`() {
        assertEquals(ZoneColors.RED, decouplingZone(10f).color)
        assertEquals(ZoneColors.RED, decouplingZone(25f).color)
    }
}
