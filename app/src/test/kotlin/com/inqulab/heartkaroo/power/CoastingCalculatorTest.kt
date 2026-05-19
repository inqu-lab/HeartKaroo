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
}
