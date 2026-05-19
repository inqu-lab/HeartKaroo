package com.inqulab.heartkaroo.cadence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OptimalCadenceCalculatorTest {

    @Test
    fun `returns null before enough bins are populated`() {
        val calc = OptimalCadenceCalculator(minSamplesPerBin = 5)
        repeat(20) { calc.add(200.0, 140.0, 92.0) } // only one bin populated
        assertNull(calc.optimalCadence())
    }

    @Test
    fun `samples below power threshold are ignored`() {
        val calc = OptimalCadenceCalculator(minSamplesPerBin = 5, minPowerW = 50.0)
        repeat(20) { calc.add(30.0, 140.0, 80.0) }
        repeat(20) { calc.add(30.0, 140.0, 100.0) }
        // No bin reaches min count because all rejected
        assertNull(calc.optimalCadence())
    }

    @Test
    fun `cadence outside guard range is ignored`() {
        val calc = OptimalCadenceCalculator(minSamplesPerBin = 5)
        repeat(20) { calc.add(200.0, 140.0, 40.0) }   // below minRpm
        repeat(20) { calc.add(200.0, 140.0, 130.0) }  // above maxRpm
        assertNull(calc.optimalCadence())
    }

    @Test
    fun `recovers the most efficient bin`() {
        // Bin centers at 55, 65, 75, 85, 95, 105, 115 (binWidth=10, min=50).
        // Feed three cadence levels with the middle one (95 RPM) most efficient.
        val calc = OptimalCadenceCalculator(minSamplesPerBin = 10)
        repeat(30) { calc.add(200.0, 160.0, 75.0) }  // eff = 200/160 = 1.25
        repeat(30) { calc.add(200.0, 130.0, 95.0) }  // eff = 200/130 ≈ 1.54  ← best
        repeat(30) { calc.add(200.0, 150.0, 110.0) } // eff = 200/150 ≈ 1.33
        val opt = calc.optimalCadence()
        assertNotNull(opt)
        assertEquals(95f, opt!!, 1e-3f)
    }

    @Test
    fun `reset clears all bins`() {
        val calc = OptimalCadenceCalculator(minSamplesPerBin = 5)
        repeat(20) { calc.add(200.0, 140.0, 80.0) }
        repeat(20) { calc.add(200.0, 130.0, 95.0) }
        assertNotNull(calc.optimalCadence())
        calc.reset()
        assertNull(calc.optimalCadence())
        assertEquals(0, calc.totalSamples)
    }

    @Test
    fun `totalSamples counts only kept samples`() {
        val calc = OptimalCadenceCalculator(minSamplesPerBin = 5, minPowerW = 50.0)
        repeat(10) { calc.add(200.0, 140.0, 95.0) }
        repeat(10) { calc.add(30.0, 140.0, 95.0) }   // rejected: low power
        repeat(10) { calc.add(200.0, 140.0, 40.0) }  // rejected: out of range
        assertEquals(10, calc.totalSamples)
    }
}
