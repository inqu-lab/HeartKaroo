package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MmpCalculatorTest {

    @Test
    fun `returns null before the window has filled`() {
        val calc = MmpCalculator(windowMs = 60_000L)
        for (s in 0..10) calc.add(s * 1000L, 300.0)
        assertNull(calc.current())
    }

    @Test
    fun `captures the peak mean once the window fills`() {
        val calc = MmpCalculator(windowMs = 60_000L)
        // 70 s at 200 W, then 70 s at 350 W, then 70 s at 200 W
        var t = 0L
        for (s in 0..70) { calc.add(t, 200.0); t += 1000L }
        for (s in 0..70) { calc.add(t, 350.0); t += 1000L }
        for (s in 0..70) { calc.add(t, 200.0); t += 1000L }
        val best = calc.current()
        assertNotNull(best)
        assertEquals(350f, best!!, 5f)
    }

    @Test
    fun `tracks 5-second peak`() {
        val calc = MmpCalculator(windowMs = 5_000L)
        var t = 0L
        // Background at 200 W for 30 s
        for (s in 0..30) { calc.add(t, 200.0); t += 1000L }
        // Sprint at 800 W for 5 s
        for (s in 0..5) { calc.add(t, 800.0); t += 1000L }
        // Back to 200 W
        for (s in 0..30) { calc.add(t, 200.0); t += 1000L }
        val best = calc.current()
        assertNotNull(best)
        assertTrue("expected sprint to dominate, got $best", best!! > 500f)
    }
}
