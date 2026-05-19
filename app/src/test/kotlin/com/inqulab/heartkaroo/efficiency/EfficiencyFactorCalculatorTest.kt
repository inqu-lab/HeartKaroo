package com.inqulab.heartkaroo.efficiency

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EfficiencyFactorCalculatorTest {

    @Test
    fun `returns null before warmup elapses`() {
        val calc = EfficiencyFactorCalculator(
            windowMs = 30 * 60 * 1000L,
            warmupMs = 10 * 60 * 1000L,
        )
        val t0 = 0L
        for (i in 0 until 60) {
            val t = t0 + i * 1000L
            calc.add(t, 200.0, 140.0)
        }
        assertNull(calc.current())
    }

    @Test
    fun `steady ride yields EF close to mean power over mean HR`() {
        val calc = EfficiencyFactorCalculator(
            windowMs = 30 * 60 * 1000L,
            warmupMs = 1 * 60 * 1000L,
            npSmoothingMs = 30_000L,
        )
        // 12 min of constant 200 W / 140 bpm
        for (s in 0..(12 * 60)) {
            calc.add(s * 1000L, 200.0, 140.0)
        }
        val ef = calc.current()
        assertNotNull(ef)
        // NP of a constant signal = the signal value; EF = 200/140 ≈ 1.4286
        assertTrue("expected EF near 1.43, got $ef", ef!! in 1.40f..1.46f)
    }

    @Test
    fun `surge ride yields NP greater than average power`() {
        val calc = EfficiencyFactorCalculator(
            windowMs = 30 * 60 * 1000L,
            warmupMs = 1 * 60 * 1000L,
            npSmoothingMs = 30_000L,
        )
        // Alternate 100 W / 300 W every 30 s; constant HR
        for (s in 0..(12 * 60)) {
            val p = if ((s / 30) % 2 == 0) 100.0 else 300.0
            calc.add(s * 1000L, p, 140.0)
        }
        val ef = calc.current()
        assertNotNull(ef)
        // Mean power = 200, EF if just-mean = 1.43; NP > mean → EF > 1.43
        assertTrue("expected EF > 1.43 due to NP bias, got $ef", ef!! > 1.43f)
    }

    @Test
    fun `samples older than the window are evicted`() {
        val calc = EfficiencyFactorCalculator(
            windowMs = 60_000L,
            warmupMs = 10_000L,
        )
        // Fill far in the past with low values
        for (s in 0..30) calc.add(s * 1000L, 50.0, 200.0)
        // Then ride at a fresh window with totally different values
        for (s in 0..120) {
            val t = 10 * 60_000L + s * 1000L
            calc.add(t, 250.0, 140.0)
        }
        val ef = calc.current()
        assertNotNull(ef)
        // Should reflect the recent ride, not the stale 50 W/200 bpm
        assertTrue("EF should reflect recent window, got $ef", ef!! > 1.5f)
    }
}
