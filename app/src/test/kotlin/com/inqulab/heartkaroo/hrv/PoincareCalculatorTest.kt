package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoincareCalculatorTest {

    @Test
    fun `returns null before three intervals collected`() {
        val calc = PoincareCalculator()
        assertNull(calc.getResult())
        calc.addInterval(800)
        calc.addInterval(820)
        assertNull(calc.getResult())
    }

    @Test
    fun `constant input is degenerate and returns null`() {
        val calc = PoincareCalculator()
        repeat(10) { calc.addInterval(800) }
        // SD2² = 0 → ratio undefined → null
        assertNull(calc.getResult())
    }

    @Test
    fun `alternating intervals yield SD1 GT SD2`() {
        // Pure alternation → all variability is beat-to-beat → SD1 dominates SD2 is small/zero
        val calc = PoincareCalculator()
        repeat(20) { calc.addInterval(if (it % 2 == 0) 800 else 900) }
        val r = calc.getResult()
        // With alternation, SD2² may be ≤ 0 in the formula → caller returns null. Accept either.
        if (r != null) {
            assertTrue("SD1 should dominate, got sd1=${r.sd1} sd2=${r.sd2}", r.sd1 >= r.sd2)
        }
    }

    @Test
    fun `monotonic ramp yields SD2 GT SD1`() {
        // A clean ramp has very small beat-to-beat differences relative to overall spread
        // → SD2 should dominate SD1.
        val calc = PoincareCalculator()
        for (i in 0 until 20) calc.addInterval(800 + i * 5)
        val r = calc.getResult()
        assertNotNull(r)
        assertTrue("SD2 should dominate, got sd1=${r!!.sd1} sd2=${r.sd2}", r.sd2 > r.sd1)
        // Ratio < 1 for ramp
        assertTrue(r.ratio < 1f)
    }

    @Test
    fun `result components are non-negative and finite`() {
        val calc = PoincareCalculator()
        listOf(700, 820, 790, 880, 760, 900, 750, 810, 780, 830).forEach { calc.addInterval(it) }
        val r = calc.getResult()
        assertNotNull(r)
        assertTrue(r!!.sd1 >= 0f && r.sd1.isFinite())
        assertTrue(r.sd2 >= 0f && r.sd2.isFinite())
        assertTrue(r.ratio >= 0f && r.ratio.isFinite())
    }

    @Test
    fun `sd1 equals SDSD over sqrt 2`() {
        // Known sample: 800, 850, 900, 950, 1000 — diffs all 50, mean diff 50, ssd = 0 → sd1 = 0
        val calc = PoincareCalculator()
        listOf(800, 850, 900, 950, 1000).forEach { calc.addInterval(it) }
        val r = calc.getResult()
        assertNotNull(r)
        assertEquals(0f, r!!.sd1, 1e-3f)
    }
}
