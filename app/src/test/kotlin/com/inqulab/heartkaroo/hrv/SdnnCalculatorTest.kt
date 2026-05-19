package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SdnnCalculatorTest {

    @Test
    fun `returns null before two intervals collected`() {
        val calc = SdnnCalculator()
        assertNull(calc.getSdnn())
        calc.addInterval(800)
        assertNull(calc.getSdnn())
    }

    @Test
    fun `SDNN of constant intervals is zero`() {
        val calc = SdnnCalculator()
        repeat(10) { calc.addInterval(800) }
        val sdnn = calc.getSdnn()
        assertNotNull(sdnn)
        assertEquals(0f, sdnn!!, 1e-3f)
    }

    @Test
    fun `SDNN matches sample stdev for a known sequence`() {
        // 700, 800, 900, 1000 — mean = 850, dev² sum = 22500+2500+2500+22500 = 50000
        // sample variance = 50000/3 = 16666.6…, stdev ≈ 129.0994
        val calc = SdnnCalculator()
        listOf(700, 800, 900, 1000).forEach { calc.addInterval(it) }
        assertEquals(129.0994f, calc.getSdnn()!!, 1e-2f)
    }

    @Test
    fun `window slides — only the last N are kept`() {
        val calc = SdnnCalculator(windowSize = 3)
        // First 3 values constant
        listOf(800, 800, 800).forEach { calc.addInterval(it) }
        assertEquals(0f, calc.getSdnn()!!, 1e-3f)
        // Add three more that drift; only last 3 should count
        calc.addInterval(700)
        calc.addInterval(900)
        calc.addInterval(800)
        // window now [700, 900, 800] → mean=800, dev² sum=10000+10000+0=20000 → var=10000 → sd=100
        assertEquals(100f, calc.getSdnn()!!, 1e-3f)
    }
}
