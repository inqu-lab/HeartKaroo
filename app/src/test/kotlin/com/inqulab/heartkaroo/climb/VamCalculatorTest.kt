package com.inqulab.heartkaroo.climb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class VamCalculatorTest {

    @Test
    fun `returns null with only one sample`() {
        val calc = VamCalculator()
        calc.add(0L, 100.0)
        assertNull(calc.current())
    }

    @Test
    fun `steady climb yields expected VAM`() {
        val calc = VamCalculator(windowMs = 60_000L)
        // 100 m gain in 60 s → 100 * 60 = 6000 m/h
        calc.add(0L, 100.0)
        calc.add(60_000L, 200.0)
        assertEquals(6000f, calc.current()!!, 50f)
    }

    @Test
    fun `descent clamps to zero`() {
        val calc = VamCalculator(windowMs = 60_000L)
        calc.add(0L, 200.0)
        calc.add(60_000L, 100.0)
        assertEquals(0f, calc.current()!!, 1e-3f)
    }

    @Test
    fun `returns null below minimum span`() {
        val calc = VamCalculator(windowMs = 60_000L)
        calc.add(0L, 100.0)
        calc.add(5_000L, 105.0)
        // < 10 s span
        assertNull(calc.current())
    }

    @Test
    fun `a stream stall restarts the window instead of spanning the gap`() {
        val calc = VamCalculator(windowMs = 60_000L)
        // Steady 6000 m/h climb, then the elevation stream stalls for 10 min
        // during which 100 m was climbed. Bridging the stall reported the
        // climb diluted over the gap (~600 m/h); the window must restart.
        calc.add(0L, 100.0)
        calc.add(30_000L, 150.0)
        assertNull(calc.add(630_000L, 250.0))
        // And once fresh post-gap samples span ≥ 10 s, VAM resumes from them.
        assertEquals(6000f, calc.add(660_000L, 300.0)!!, 50f)
    }
}
