package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerMetricsTest {

    @Test
    fun `variability index is NP over AP`() {
        assertEquals(1.0f, PowerMetrics.variabilityIndex(200f, 200f)!!, 1e-6f)
        assertEquals(1.05f, PowerMetrics.variabilityIndex(210f, 200f)!!, 1e-6f)
    }

    @Test
    fun `variability index is null when AP is zero or inputs missing`() {
        assertNull(PowerMetrics.variabilityIndex(200f, 0f))
        assertNull(PowerMetrics.variabilityIndex(null, 200f))
        assertNull(PowerMetrics.variabilityIndex(200f, null))
    }

    @Test
    fun `intensity factor is NP over FTP`() {
        assertEquals(1.0f, PowerMetrics.intensityFactor(270f, 270)!!, 1e-6f)
        assertEquals(0.5f, PowerMetrics.intensityFactor(135f, 270)!!, 1e-6f)
    }

    @Test
    fun `intensity factor floors FTP at 1 to avoid divide by zero`() {
        assertEquals(50f, PowerMetrics.intensityFactor(50f, 0)!!, 1e-6f)
        assertEquals(50f, PowerMetrics.intensityFactor(50f, -10)!!, 1e-6f)
    }

    @Test
    fun `intensity factor is null without NP`() {
        assertNull(PowerMetrics.intensityFactor(null, 270))
    }

    @Test
    fun `TSS is one hundred per hour at IF of one`() {
        // NP == FTP -> IF = 1; one hour -> TSS 100, two hours -> 200.
        assertEquals(100f, PowerMetrics.trainingStressScore(270f, 270, 3600.0)!!, 1e-3f)
        assertEquals(200f, PowerMetrics.trainingStressScore(270f, 270, 7200.0)!!, 1e-3f)
    }

    @Test
    fun `TSS scales with IF squared`() {
        // IF = 0.5 over one hour -> 0.5^2 * 100 = 25.
        assertEquals(25f, PowerMetrics.trainingStressScore(135f, 270, 3600.0)!!, 1e-3f)
    }

    @Test
    fun `TSS is zero with no elapsed time and null without NP`() {
        assertEquals(0f, PowerMetrics.trainingStressScore(270f, 270, 0.0)!!, 1e-6f)
        assertNull(PowerMetrics.trainingStressScore(null, 270, 3600.0))
    }

    @Test
    fun `eFTP is 95 percent of best 20-minute power`() {
        assertEquals(285f, PowerMetrics.eftp(300f)!!, 1e-3f)
        assertNull(PowerMetrics.eftp(null))
    }

    @Test
    fun `W-prime percent is the balance share of W-prime-zero`() {
        assertEquals(50f, PowerMetrics.wPrimePercent(10_000f, 20_000)!!, 1e-3f)
        assertEquals(100f, PowerMetrics.wPrimePercent(20_000f, 20_000)!!, 1e-3f)
        assertNull(PowerMetrics.wPrimePercent(null, 20_000))
    }

    @Test
    fun `W-prime percent clamps to the gauge and floors W-prime-zero at 1`() {
        // Overdrawn model reads 0, not negative.
        assertEquals(0f, PowerMetrics.wPrimePercent(-500f, 20_000)!!, 1e-6f)
        // A non-positive W′₀ can't divide; the floor keeps it finite and clamped.
        assertEquals(100f, PowerMetrics.wPrimePercent(500f, 0)!!, 1e-6f)
    }
}
