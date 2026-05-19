package com.inqulab.heartkaroo.efficiency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CardiacCostCalculatorTest {

    @Test
    fun `returns null before warmup elapses`() {
        val calc = CardiacCostCalculator(warmupMs = 10 * 60 * 1000L)
        for (s in 0..60) calc.add(s * 1000L, 200.0, 140.0)
        assertNull(calc.current())
    }

    @Test
    fun `steady ride yields HR over power`() {
        val calc = CardiacCostCalculator(warmupMs = 60 * 1000L)
        for (s in 0..120) calc.add(s * 1000L, 200.0, 140.0)
        val cc = calc.current()
        assertNotNull(cc)
        // 140 / 200 = 0.70
        assertEquals(0.70f, cc!!, 0.01f)
    }

    @Test
    fun `reset clears the window`() {
        val calc = CardiacCostCalculator(warmupMs = 60 * 1000L)
        for (s in 0..120) calc.add(s * 1000L, 200.0, 140.0)
        calc.reset()
        assertNull(calc.current())
    }
}
