package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizedPowerCalculatorTest {

    @Test
    fun `returns null before minimum samples`() {
        val np = NormalizedPowerCalculator(minSamples = 30)
        for (i in 0 until 10) np.add(i * 1000L, 200.0)
        assertNull(np.normalizedPower())
    }

    @Test
    fun `constant power yields NP equal to that power`() {
        val np = NormalizedPowerCalculator(minSamples = 30, smoothingMs = 30_000L)
        for (i in 0..600) np.add(i * 1000L, 200.0)
        assertEquals(200f, np.normalizedPower()!!, 1f)
        assertEquals(200f, np.averagePower()!!, 1f)
    }

    @Test
    fun `elapsedSec is zero with fewer than two samples`() {
        val np = NormalizedPowerCalculator()
        assertEquals(0.0, np.elapsedSec(), 1e-9)
        np.add(1_000L, 200.0)
        assertEquals(0.0, np.elapsedSec(), 1e-9)
    }

    @Test
    fun `elapsedSec spans first to last retained sample`() {
        val np = NormalizedPowerCalculator()
        np.add(1_000L, 200.0)
        np.add(6_000L, 200.0)
        assertEquals(5.0, np.elapsedSec(), 1e-9)
    }

    @Test
    fun `spiky power yields NP greater than AP`() {
        val np = NormalizedPowerCalculator(minSamples = 30, smoothingMs = 30_000L)
        for (s in 0..600) {
            val p = if ((s / 30) % 2 == 0) 100.0 else 300.0
            np.add(s * 1000L, p)
        }
        val n = np.normalizedPower()!!
        val a = np.averagePower()!!
        assertTrue("NP should exceed AP for spiky input, np=$n ap=$a", n > a)
        assertEquals(200f, a, 5f)
    }
}
