package com.inqulab.heartkaroo.decoupling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecouplingCalculatorTest {

    private val secondMs = 1000L
    private val minuteMs = 60_000L

    @Test
    fun `returns null before the warmup window has filled`() {
        val calc = DecouplingCalculator()
        // 5 minutes of samples — under the 10-min warmup
        for (i in 0 until 5 * 60) {
            calc.add(i * secondMs, power = 200.0, hr = 150.0)
        }
        assertNull(calc.current())
    }

    @Test
    fun `returns zero decoupling for perfectly stable power and HR`() {
        val calc = DecouplingCalculator()
        // 20 minutes of samples — past warmup
        for (i in 0 until 20 * 60) {
            calc.add(i * secondMs, power = 200.0, hr = 150.0)
        }
        val result = calc.current()
        assertNotNull(result)
        assertEquals(0.0, result!!, 1e-9)
    }

    @Test
    fun `detects positive decoupling when HR drifts up at constant power`() {
        val calc = DecouplingCalculator()
        // First half: HR 150. Second half: HR 165. Power constant 200.
        // r1 = 200/150 = 1.333, r2 = 200/165 = 1.212
        // decoupling = (1.333 - 1.212)/1.333 * 100 ≈ 9.09%
        val halfSeconds = 10 * 60
        for (i in 0 until halfSeconds) {
            calc.add(i * secondMs, power = 200.0, hr = 150.0)
        }
        for (i in 0 until halfSeconds) {
            calc.add((halfSeconds + i) * secondMs, power = 200.0, hr = 165.0)
        }
        val result = calc.current()
        assertNotNull(result)
        assertEquals(9.09, result!!, 0.05)
    }

    @Test
    fun `detects negative decoupling when HR drops at constant power`() {
        val calc = DecouplingCalculator()
        val halfSeconds = 10 * 60
        for (i in 0 until halfSeconds) {
            calc.add(i * secondMs, power = 200.0, hr = 160.0)
        }
        for (i in 0 until halfSeconds) {
            calc.add((halfSeconds + i) * secondMs, power = 200.0, hr = 150.0)
        }
        val result = calc.current()
        assertNotNull(result)
        assertTrue("expected negative decoupling, got $result", result!! < 0.0)
    }

    @Test
    fun `ignores samples with zero or negative power or HR`() {
        val calc = DecouplingCalculator()
        val baseline = DecouplingCalculator()
        for (i in 0 until 20 * 60) {
            val t = i * secondMs
            calc.add(t, power = 200.0, hr = 150.0)
            baseline.add(t, power = 200.0, hr = 150.0)
        }
        // Injecting bad samples should not perturb the result.
        calc.add(20 * 60 * secondMs, power = 0.0, hr = 150.0)
        calc.add(20 * 60 * secondMs + 1, power = 200.0, hr = 0.0)
        calc.add(20 * 60 * secondMs + 2, power = -50.0, hr = 150.0)

        assertEquals(baseline.current()!!, calc.current()!!, 1e-9)
    }

    @Test
    fun `evicts samples older than the rolling window`() {
        val calc = DecouplingCalculator(windowMs = 30 * minuteMs, warmupMs = 10 * minuteMs)
        // Bad first-half data at t=0..10 min that would skew decoupling positive
        for (i in 0 until 10 * 60) {
            calc.add(i * secondMs, power = 100.0, hr = 100.0)
        }
        // Clean stable data for the next 35 minutes
        for (i in 0 until 35 * 60) {
            val t = (10 * 60 + i) * secondMs
            calc.add(t, power = 200.0, hr = 150.0)
        }
        // The first batch has now scrolled out of the 30-min window.
        val result = calc.current()
        assertNotNull(result)
        assertEquals(0.0, result!!, 1e-9)
    }

    @Test
    fun `reset clears state`() {
        val calc = DecouplingCalculator()
        for (i in 0 until 20 * 60) {
            calc.add(i * secondMs, power = 200.0, hr = 150.0)
        }
        assertNotNull(calc.current())
        calc.reset()
        assertNull(calc.current())
    }

    @Test
    fun `current returns null when called with no samples`() {
        assertNull(DecouplingCalculator().current())
    }
}
