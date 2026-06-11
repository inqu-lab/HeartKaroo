package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CumulativeNormalizedPowerCalculatorTest {

    @Test
    fun `needs minimum samples before reporting`() {
        val calc = CumulativeNormalizedPowerCalculator()
        repeat(29) { calc.add(it * 1000L, 200.0) }
        assertNull(calc.normalizedPower())
    }

    @Test
    fun `constant power NP equals the power`() {
        val calc = CumulativeNormalizedPowerCalculator()
        for (s in 0..600) calc.add(s * 1000L, 250.0)
        assertEquals(250f, calc.normalizedPower()!!, 0.5f)
        assertEquals(600.0, calc.elapsedSec(), 1e-6)
    }

    @Test
    fun `elapsed time and TSS keep growing past four hours`() {
        // The old 4-h-windowed NP calculator evicted early samples, so TSS
        // saturated at the window length on long rides. Cumulative TSS at
        // FTP is 100 per hour: a 6-h ride must read ~600, not ~400.
        val calc = CumulativeNormalizedPowerCalculator()
        val ftp = 250
        for (s in 0 until 6 * 3600) calc.add(s * 1000L, ftp.toDouble())
        assertEquals(6 * 3600.0 - 1.0, calc.elapsedSec(), 1e-6)
        val tss = PowerMetrics.trainingStressScore(calc.normalizedPower(), ftp, calc.elapsedSec())
        assertEquals(600f, tss!!, 2f)
    }

    @Test
    fun `TSS never decreases when a hard start scrolls past the old window length`() {
        // Hard first hour, then easy: with eviction the hard hour eventually
        // dropped out of NP and the "cumulative" TSS shrank. It must only grow.
        val calc = CumulativeNormalizedPowerCalculator()
        val ftp = 250
        var t = 0L
        var lastTss = 0f
        for (s in 0 until 3600) { calc.add(t, 300.0); t += 1000L }
        for (hour in 0 until 5) {
            for (s in 0 until 3600) { calc.add(t, 150.0); t += 1000L }
            val tss = PowerMetrics.trainingStressScore(
                calc.normalizedPower(), ftp, calc.elapsedSec(),
            )!!
            assertTrue("TSS shrank: $tss after $lastTss", tss >= lastTss)
            lastTss = tss
        }
    }

    @Test
    fun `matches the windowed calculator while inside its window`() {
        val cumulative = CumulativeNormalizedPowerCalculator()
        val windowed = NormalizedPowerCalculator()
        for (s in 0..1800) {
            val p = if (s % 60 < 30) 280.0 else 140.0
            cumulative.add(s * 1000L, p)
            windowed.add(s * 1000L, p)
        }
        assertEquals(windowed.normalizedPower()!!, cumulative.normalizedPower()!!, 0.5f)
        assertEquals(windowed.elapsedSec(), cumulative.elapsedSec(), 1e-6)
    }

    @Test
    fun `reset clears everything`() {
        val calc = CumulativeNormalizedPowerCalculator()
        for (s in 0..600) calc.add(s * 1000L, 250.0)
        calc.reset()
        assertNull(calc.normalizedPower())
        assertEquals(0.0, calc.elapsedSec(), 1e-9)
    }
}
