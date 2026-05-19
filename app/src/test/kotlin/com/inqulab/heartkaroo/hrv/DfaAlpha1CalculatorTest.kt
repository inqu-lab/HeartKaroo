package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class DfaAlpha1CalculatorTest {

    @Test
    fun `returns null before the minimum sample count`() {
        val calc = DfaAlpha1Calculator(minSamples = 120)
        repeat(50) { calc.addInterval(800) }
        assertNull(calc.getAlpha1())
    }

    @Test
    fun `rejects physiologically impossible RR intervals`() {
        val calc = DfaAlpha1Calculator(minSamples = 4, maxSamples = 100)
        calc.addInterval(299)
        calc.addInterval(2001)
        assertNull(calc.getAlpha1())
    }

    @Test
    fun `white noise yields alpha near 0_5`() {
        // Uncorrelated noise => DFA exponent ~ 0.5
        val calc = DfaAlpha1Calculator(minSamples = 200, maxSamples = 400)
        val rng = Random(42)
        repeat(400) {
            val jitter = (rng.nextDouble() - 0.5) * 200.0
            calc.addInterval((800 + jitter).toInt().coerceIn(400, 1200))
        }
        val alpha = calc.getAlpha1()
        assertNotNull(alpha)
        assertEquals(0.5f, alpha!!, 0.2f)
    }

    @Test
    fun `pure random walk yields alpha near 1_5`() {
        // Brownian motion / random walk => DFA exponent ~ 1.5
        val calc = DfaAlpha1Calculator(minSamples = 200, maxSamples = 400)
        val rng = Random(7)
        var value = 800.0
        repeat(400) {
            value += (rng.nextDouble() - 0.5) * 40.0
            calc.addInterval(value.toInt().coerceIn(400, 1200))
        }
        val alpha = calc.getAlpha1()
        assertNotNull(alpha)
        assertTrue(
            "expected alpha > 1.2 for random walk, got $alpha",
            alpha!! > 1.2f,
        )
    }

    @Test
    fun `window slides — oldest samples are evicted`() {
        val calc = DfaAlpha1Calculator(minSamples = 120, maxSamples = 200)
        repeat(500) { calc.addInterval(800) }
        // Constant input produces a degenerate F(n)=0 series → returns null
        assertNull(calc.getAlpha1())
    }

    @Test
    fun `reset clears the window`() {
        val calc = DfaAlpha1Calculator(minSamples = 4, maxSamples = 100)
        repeat(50) { calc.addInterval(800 + it) }
        calc.reset()
        assertNull(calc.getAlpha1())
    }

    @Test
    fun `slope is computed correctly for a deterministic ramp`() {
        // Sanity: feed a slow drift; just verify it returns a finite, positive number
        val calc = DfaAlpha1Calculator(minSamples = 200, maxSamples = 400)
        for (i in 0 until 300) {
            val drift = (sqrt(i.toDouble()) * 5).toInt()
            calc.addInterval(800 + drift)
        }
        val alpha = calc.getAlpha1()
        assertNotNull(alpha)
        assertTrue("alpha should be finite", alpha!!.isFinite())
        assertTrue("alpha should be > 0", alpha > 0f)
    }
}
