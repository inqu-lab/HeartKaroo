package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class HRVCalculatorTest {

    @Test
    fun `getRmssd returns zero before two intervals have been collected`() {
        val calc = HRVCalculator()
        assertEquals(0f, calc.getRmssd(), 0f)
        assertFalse(calc.hasData)
        calc.addInterval(800)
        assertEquals(0f, calc.getRmssd(), 0f)
        assertFalse(calc.hasData)
    }

    @Test
    fun `getRmssd matches the RMSSD formula for a known sequence`() {
        // Successive differences: 50, 50, 50 → RMSSD = sqrt((2500*3)/3) = 50
        val calc = HRVCalculator()
        listOf(800, 850, 900, 950).forEach { calc.addInterval(it) }
        assertTrue(calc.hasData)
        assertEquals(50f, calc.getRmssd(), 1e-3f)
    }

    @Test
    fun `getRmssd handles alternating intervals`() {
        // Diffs: -50, +50, -50, +50 → all squared = 2500 → mean = 2500 → sqrt = 50
        val calc = HRVCalculator()
        listOf(900, 850, 900, 850, 900).forEach { calc.addInterval(it) }
        assertEquals(50f, calc.getRmssd(), 1e-3f)
    }

    @Test
    fun `addInterval rejects values outside the physiological range`() {
        val calc = HRVCalculator()
        calc.addInterval(800)
        calc.addInterval(299)   // too low — rejected
        calc.addInterval(2001)  // too high — rejected
        calc.addInterval(800)
        // Only the two 800ms values were accepted → diff = 0 → RMSSD = 0
        assertEquals(0f, calc.getRmssd(), 0f)
    }

    @Test
    fun `addInterval accepts the boundary values 300 and 2000`() {
        val calc = HRVCalculator()
        calc.addInterval(300)
        calc.addInterval(2000)
        assertTrue(calc.hasData)
    }

    @Test
    fun `window slides — oldest intervals are evicted`() {
        val calc = HRVCalculator(windowSize = 3)
        // Fill window with constant values → RMSSD = 0
        listOf(800, 800, 800).forEach { calc.addInterval(it) }
        assertEquals(0f, calc.getRmssd(), 0f)
        // Push two more values that vary; only the last 3 should count
        calc.addInterval(900) // window now [800, 800, 900]
        calc.addInterval(800) // window now [800, 900, 800]
        // Diffs: +100, -100 → RMSSD = sqrt((10000+10000)/2) = 100
        assertEquals(100f, calc.getRmssd(), 1e-3f)
    }

    @Test
    fun `RMSSD is computed with N-1 denominator over diffs`() {
        // 3 intervals → 2 diffs. Diffs: 10, 20.
        // RMSSD = sqrt((100 + 400) / 2) = sqrt(250)
        val calc = HRVCalculator()
        listOf(700, 710, 730).forEach { calc.addInterval(it) }
        assertEquals(sqrt(250.0).toFloat(), calc.getRmssd(), 1e-3f)
    }
}
