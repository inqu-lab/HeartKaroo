package com.inqulab.heartkaroo.wprime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WPrimeBalanceCalculatorTest {

    @Test
    fun `starts at W prime initial value`() {
        val calc = WPrimeBalanceCalculator(criticalPowerW = 250.0, wPrimeJ = 20_000.0)
        assertEquals(20_000f, calc.current(), 1e-3f)
    }

    @Test
    fun `power above CP depletes the balance linearly`() {
        val calc = WPrimeBalanceCalculator(criticalPowerW = 250.0, wPrimeJ = 20_000.0)
        calc.add(0L, 250.0) // priming sample
        // 100 W above CP for 60 s → 6 000 J burned
        for (s in 1..60) calc.add(s * 1000L, 350.0)
        assertEquals(14_000f, calc.current(), 200f)
    }

    @Test
    fun `power below CP recovers the balance toward W prime`() {
        val calc = WPrimeBalanceCalculator(criticalPowerW = 250.0, wPrimeJ = 20_000.0)
        calc.add(0L, 250.0)
        // Burn 10 000 J over 100 s at 100 W above CP
        for (s in 1..100) calc.add(s * 1000L, 350.0)
        val depleted = calc.current()
        assertTrue("expected balance to drop, got $depleted", depleted < 12_000f)
        // Then sit at 100 W (well under CP) for 10 min
        var t = 100 * 1000L
        for (s in 1..600) {
            t += 1000L
            calc.add(t, 100.0)
        }
        val recovered = calc.current()
        assertTrue(
            "expected recovery toward W′₀, depleted=$depleted recovered=$recovered",
            recovered > depleted + 1_000f,
        )
        assertTrue("recovered should not exceed W′₀", recovered <= 20_000f + 1e-3f)
    }

    @Test
    fun `balance is clamped at zero`() {
        val calc = WPrimeBalanceCalculator(criticalPowerW = 250.0, wPrimeJ = 5_000.0)
        calc.add(0L, 250.0)
        // 1 000 W (750 over CP) for 60 s = 45 000 J demand, only 5 kJ available
        for (s in 1..60) calc.add(s * 1000L, 1_000.0)
        assertEquals(0f, calc.current(), 1e-3f)
    }

    @Test
    fun `reset restores initial value`() {
        val calc = WPrimeBalanceCalculator(wPrimeJ = 20_000.0)
        calc.add(0L, 500.0)
        calc.add(60_000L, 500.0)
        calc.reset()
        assertEquals(20_000f, calc.current(), 1e-3f)
    }
}
