package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WattsPerKgCalculatorTest {

    @Test
    fun `divides smoothed power by weight`() {
        val calc = WattsPerKgCalculator(weightKg = 75f)
        assertEquals(4f, calc.add(0L, 300.0)!!, 1e-6f)
    }

    @Test
    fun `smooths power over the window`() {
        val calc = WattsPerKgCalculator(weightKg = 100f, windowMs = 3_000L)
        calc.add(0L, 100.0)
        calc.add(1000L, 200.0)
        // Mean of 100/200/300 = 200 W over 100 kg.
        assertEquals(2f, calc.add(2000L, 300.0)!!, 1e-6f)
    }

    @Test
    fun `samples older than the window drop out`() {
        val calc = WattsPerKgCalculator(weightKg = 100f, windowMs = 3_000L)
        calc.add(0L, 1000.0)
        calc.add(5000L, 200.0)
        // The 1000 W sample is past the 3 s window; only 200/200 remain.
        assertEquals(2f, calc.add(6000L, 200.0)!!, 1e-6f)
    }

    @Test
    fun `null with a non-positive weight`() {
        val calc = WattsPerKgCalculator(weightKg = 0f)
        assertNull(calc.add(0L, 300.0))
    }

    @Test
    fun `null before any sample and invalid samples ignored`() {
        val calc = WattsPerKgCalculator(weightKg = 75f)
        assertNull(calc.current())
        assertNull(calc.add(0L, -50.0))
        assertNull(calc.add(0L, Double.NaN))
        assertEquals(4f, calc.add(0L, 300.0)!!, 1e-6f)
    }
}
