package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Test

class DfaZoneTest {

    @Test
    fun `at or above LT1 is the below-LT1 zone`() {
        assertEquals(DfaZone.BELOW_LT1, classifyDfaZone(0.75))
        assertEquals(DfaZone.BELOW_LT1, classifyDfaZone(0.90))
    }

    @Test
    fun `between LT2 and LT1 is the LT1-to-LT2 zone`() {
        assertEquals(DfaZone.LT1_TO_LT2, classifyDfaZone(0.74))
        assertEquals(DfaZone.LT1_TO_LT2, classifyDfaZone(0.62))
        assertEquals(DfaZone.LT1_TO_LT2, classifyDfaZone(0.50))
    }

    @Test
    fun `below LT2 is the above-LT2 zone`() {
        assertEquals(DfaZone.ABOVE_LT2, classifyDfaZone(0.49))
        assertEquals(DfaZone.ABOVE_LT2, classifyDfaZone(0.30))
    }
}
