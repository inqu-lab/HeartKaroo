package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class Pnn50CalculatorTest {

    @Test
    fun `returns null before two intervals collected`() {
        val calc = Pnn50Calculator()
        assertNull(calc.getPnn50())
        calc.addInterval(800)
        assertNull(calc.getPnn50())
    }

    @Test
    fun `constant intervals yield zero`() {
        val calc = Pnn50Calculator()
        repeat(10) { calc.addInterval(800) }
        assertEquals(0f, calc.getPnn50()!!, 1e-3f)
    }

    @Test
    fun `large alternating swings yield 100 percent`() {
        val calc = Pnn50Calculator()
        // Diffs all > 50 ms
        listOf(800, 900, 800, 900, 800).forEach { calc.addInterval(it) }
        assertEquals(100f, calc.getPnn50()!!, 1e-3f)
    }

    @Test
    fun `mixed sequence is counted correctly`() {
        val calc = Pnn50Calculator()
        // Diffs: +49 (no), +50 (no, strict >), +51 (yes), -10 (no)
        listOf(800, 849, 899, 950, 940).forEach { calc.addInterval(it) }
        assertNotNull(calc.getPnn50())
        // 1 of 4 diffs > 50 → 25%
        assertEquals(25f, calc.getPnn50()!!, 1e-3f)
    }
}
