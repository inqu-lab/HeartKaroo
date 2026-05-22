package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuadrantAnalysisCalculatorTest {

    @Test
    fun `returns null with no samples`() {
        val calc = QuadrantAnalysisCalculator(ftpW = 270.0)
        assertNull(calc.dominantQuadrant())
    }

    @Test
    fun `high force low cadence is quadrant 2`() {
        // FTP=270, 50 rpm grinder at 350 W:
        // refCpv at 90 rpm = 2π * 0.175 * 1.5 ≈ 1.649 m/s
        // CPV at 50 rpm = 2π * 0.175 * 0.833 ≈ 0.916 m/s → LOW
        // FP = 350 / 0.916 ≈ 382 N
        // refFp = 270 / 1.649 ≈ 164 N → high → quadrant 2
        val calc = QuadrantAnalysisCalculator(ftpW = 270.0)
        for (i in 0..30) calc.add(i * 1000L, 350.0, 50.0)
        assertEquals(2, calc.dominantQuadrant())
    }

    @Test
    fun `low force high cadence is quadrant 4`() {
        // 110 rpm at 150 W
        // CPV = 2π * 0.175 * (110/60) ≈ 2.016 m/s → high
        // FP = 150 / 2.016 ≈ 74 N → low → quadrant 4
        val calc = QuadrantAnalysisCalculator(ftpW = 270.0)
        for (i in 0..30) calc.add(i * 1000L, 150.0, 110.0)
        assertEquals(4, calc.dominantQuadrant())
    }

    @Test
    fun `low force low cadence is quadrant 3`() {
        // 70 rpm at 100 W
        val calc = QuadrantAnalysisCalculator(ftpW = 270.0)
        for (i in 0..30) calc.add(i * 1000L, 100.0, 70.0)
        assertEquals(3, calc.dominantQuadrant())
    }

    @Test
    fun `distribution is null before any sample`() {
        assertNull(QuadrantAnalysisCalculator(ftpW = 270.0).distributionPercent())
    }

    @Test
    fun `distribution reports cumulative share per quadrant`() {
        val calc = QuadrantAnalysisCalculator(ftpW = 270.0)
        repeat(30) { i -> calc.add(i * 1000L, 350.0, 50.0) }            // Q2
        repeat(10) { i -> calc.add((100 + i) * 1000L, 150.0, 110.0) }   // Q4
        val d = calc.distributionPercent()!!
        assertEquals(0.0, d[0], 1e-9)
        assertEquals(75.0, d[1], 1e-9)
        assertEquals(0.0, d[2], 1e-9)
        assertEquals(25.0, d[3], 1e-9)
    }
}
