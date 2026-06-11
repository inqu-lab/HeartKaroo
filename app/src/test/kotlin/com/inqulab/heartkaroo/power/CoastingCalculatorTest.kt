package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoastingCalculatorTest {

    @Test
    fun `returns null before warmup elapses`() {
        val calc = CoastingCalculator(minWarmupMs = 60_000L)
        for (s in 0..30) calc.add(s * 1000L, 200.0)
        assertNull(calc.current())
    }

    @Test
    fun `always pedalling yields zero`() {
        val calc = CoastingCalculator(minWarmupMs = 30_000L)
        for (s in 0..120) calc.add(s * 1000L, 200.0)
        assertEquals(0f, calc.current()!!, 1e-3f)
    }

    @Test
    fun `half coasting yields 50 percent`() {
        val calc = CoastingCalculator(coastWatts = 5.0, minWarmupMs = 30_000L)
        var t = 0L
        // 60 s pedalling at 200 W
        for (s in 0..60) { calc.add(t, 200.0); t += 1000L }
        // 60 s coasting at 0 W
        for (s in 0..60) { calc.add(t, 0.0); t += 1000L }
        // Total ≈ 120 s, half coasting → ~50%
        assertEquals(50f, calc.current()!!, 2f)
    }

    @Test
    fun `a sensor dropout is not credited to the pre-gap state`() {
        val calc = CoastingCalculator(coastWatts = 5.0, minWarmupMs = 30_000L)
        var t = 0L
        // 60 s pedalling, 60 s coasting, then a 10-minute dropout, then 120 s
        // pedalling. The gap must not count as 600 s of "coasting" (which read
        // ~79 %); true split is 60 s coasting of 240 s riding = 25 %.
        for (s in 0 until 60) { calc.add(t, 200.0); t += 1000L }
        for (s in 0 until 60) { calc.add(t, 0.0); t += 1000L }
        t += 600_000L
        for (s in 0 until 120) { calc.add(t, 200.0); t += 1000L }
        assertEquals(25f, calc.current()!!, 2f)
    }
}
