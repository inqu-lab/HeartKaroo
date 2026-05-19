package com.inqulab.heartkaroo.aet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AerobicThresholdCalibratorTest {

    private fun feed(
        calc: AerobicThresholdCalibrator,
        sample: Int,
        powerFn: (Int) -> Double,
        alphaFn: (Int) -> Float,
        msPerSample: Long = 1_000L,
    ) {
        var t = 0L
        for (i in 0 until sample) {
            calc.addPower(t, powerFn(i))
            calc.addAlpha(alphaFn(i))
            t += msPerSample
        }
    }

    @Test
    fun `returns null before minimum sample count`() {
        val calc = AerobicThresholdCalibrator(minSamples = 60)
        feed(calc, 10, { 200.0 }, { 0.8f })
        assertNull(calc.currentEstimate())
    }

    @Test
    fun `recovers known crossing for synthetic linear decay`() {
        // α(P) = 1.5 - 0.005 * P  =>  α = 0.75 at P = 150 W
        val calc = AerobicThresholdCalibrator(
            minSamples = 50,
            powerSmoothingMs = 1_000L,
        )
        feed(
            calc, 200,
            powerFn = { i -> 50.0 + i.toDouble() },                       // 50..249 W
            alphaFn = { i -> (1.5 - 0.005 * (50.0 + i)).toFloat() },      // 1.25..0.255
        )
        val est = calc.currentEstimate()
        assertNotNull(est)
        assertEquals(150f, est!!, 2f)
    }

    @Test
    fun `flat alpha gives no estimate`() {
        val calc = AerobicThresholdCalibrator(minSamples = 50, powerSmoothingMs = 1_000L)
        feed(calc, 200, { i -> 100.0 + i }, { 0.8f })
        assertNull(calc.currentEstimate())
    }

    @Test
    fun `positive slope is rejected`() {
        // α rising with power — physiologically wrong direction
        val calc = AerobicThresholdCalibrator(minSamples = 50, powerSmoothingMs = 1_000L)
        feed(
            calc, 200,
            powerFn = { i -> 100.0 + i },
            alphaFn = { i -> 0.5f + 0.001f * i },
        )
        assertNull(calc.currentEstimate())
    }

    @Test
    fun `alpha outside guard range is ignored`() {
        val calc = AerobicThresholdCalibrator(
            minSamples = 10,
            alphaMin = 0.4,
            alphaMax = 1.2,
            powerSmoothingMs = 1_000L,
        )
        // Power present, alphas all out of range
        repeat(50) { i ->
            calc.addPower((i * 1000L), 200.0)
            calc.addAlpha(2.0f)  // > alphaMax
            calc.addAlpha(0.1f)  // < alphaMin
        }
        assertEquals(0, calc.sampleCount)
        assertNull(calc.currentEstimate())
    }

    @Test
    fun `reset clears state`() {
        val calc = AerobicThresholdCalibrator(minSamples = 50, powerSmoothingMs = 1_000L)
        feed(
            calc, 200,
            powerFn = { i -> 50.0 + i.toDouble() },
            alphaFn = { i -> (1.5 - 0.005 * (50.0 + i)).toFloat() },
        )
        assertNotNull(calc.currentEstimate())
        calc.reset()
        assertEquals(0, calc.sampleCount)
        assertNull(calc.currentEstimate())
    }

    @Test
    fun `power smoothing averages over the smoothing window`() {
        val calc = AerobicThresholdCalibrator(
            minSamples = 5,
            powerSmoothingMs = 30_000L,
        )
        // Burst 200 W for 30s, then take alpha → smoothed power ≈ 200
        for (s in 0 until 30) calc.addPower(s * 1_000L, 200.0)
        calc.addAlpha(0.9f)
        // Then high-intensity 400 W for 30 s, sample again
        for (s in 30 until 60) calc.addPower(s * 1_000L, 400.0)
        calc.addAlpha(0.6f)
        // The two paired points should bracket 0.75 around 300 W (midpoint)
        val est = calc.currentEstimate()
        assertNotNull(est)
        assertTrue("estimate should fall between the two power levels, got $est", est!! in 200f..400f)
    }
}
