package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HRVStressCalculatorTest {

    private val minuteMs = 60_000L

    private class FakeClock(var nowMs: Long = 0L) : () -> Long {
        override fun invoke(): Long = nowMs
    }

    @Test
    fun `returns null before the first sample`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        assertNull(calc.getStressPct())
    }

    @Test
    fun `returns null during warmup`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        calc.addRmssd(40f)
        // Still inside the 5-min warmup
        clock.nowMs = 4 * minuteMs
        calc.addRmssd(40f)
        assertNull(calc.getStressPct())
    }

    @Test
    fun `stable RMSSD yields zero stress after warmup`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        // Feed a constant RMSSD across 6 min in 5-second steps
        for (i in 0..(6 * 60 / 5)) {
            clock.nowMs = (i * 5_000L)
            calc.addRmssd(50f)
        }
        val result = calc.getStressPct()
        assertNotNull(result)
        assertEquals(0f, result!!, 0.5f)
    }

    @Test
    fun `dropping RMSSD below baseline produces positive stress`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        // Establish baseline at 60 ms over 30 min — well past tau, so EMA settles
        for (i in 0..(30 * 60 / 5)) {
            clock.nowMs = (i * 5_000L)
            calc.addRmssd(60f)
        }
        // Single sharp drop to 30 ms. With tau = 20 min and dt = 5 s,
        // alpha ≈ 0.004, so baseline barely moves → stress ≈ 50%.
        clock.nowMs += 5_000L
        calc.addRmssd(30f)
        val result = calc.getStressPct()
        assertNotNull(result)
        assertTrue("expected ~50% stress, got $result", result!! in 45f..55f)
    }

    @Test
    fun `RMSSD above baseline is clamped to zero`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        for (i in 0..(20 * 60 / 5)) {
            clock.nowMs = (i * 5_000L)
            calc.addRmssd(40f)
        }
        clock.nowMs += 5_000L
        calc.addRmssd(80f)
        assertEquals(0f, calc.getStressPct()!!, 1e-3f)
    }

    @Test
    fun `baseline converges toward new RMSSD over tau`() {
        val clock = FakeClock()
        val tau = 10 * minuteMs
        val calc = HRVStressCalculator(tauMs = tau, warmupMs = minuteMs, clockMs = clock)
        // Seed baseline at 100
        calc.addRmssd(100f)
        // Then jump to 50 and hold for 5 * tau — baseline should have converged
        for (i in 1..(5 * 10 * 60 / 5)) {
            clock.nowMs = (i * 5_000L)
            calc.addRmssd(50f)
        }
        // With baseline ≈ 50 and current 50, stress ≈ 0
        val result = calc.getStressPct()
        assertNotNull(result)
        assertEquals(0f, result!!, 1f)
    }

    @Test
    fun `reset clears baseline and warmup timer`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        for (i in 0..(10 * 60 / 5)) {
            clock.nowMs = (i * 5_000L)
            calc.addRmssd(50f)
        }
        assertNotNull(calc.getStressPct())
        calc.reset()
        assertNull(calc.getStressPct())
        // After reset, warmup gate applies again
        calc.addRmssd(50f)
        assertNull(calc.getStressPct())
    }

    @Test
    fun `ignores non-positive rmssd values`() {
        val clock = FakeClock()
        val calc = HRVStressCalculator(clockMs = clock)
        calc.addRmssd(0f)
        calc.addRmssd(-5f)
        clock.nowMs = 6 * minuteMs
        // Warmup timer should not have started; result still null
        assertNull(calc.getStressPct())
    }
}
