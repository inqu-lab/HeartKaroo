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
    fun `estimateForTarget solves the same fit for other thresholds`() {
        // α(P) = 1.5 - 0.005 * P  =>  α = 0.75 at 150 W, α = 0.50 at 200 W
        val calc = AerobicThresholdCalibrator(minSamples = 50, powerSmoothingMs = 1_000L)
        feed(
            calc, 200,
            powerFn = { i -> 50.0 + i.toDouble() },
            alphaFn = { i -> (1.5 - 0.005 * (50.0 + i)).toFloat() },
        )
        assertEquals(150f, calc.estimateForTarget(0.75)!!, 3f)
        assertEquals(200f, calc.estimateForTarget(0.50)!!, 3f)
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
        // Five alternating bursts at 200 W / 400 W, sampling alpha after
        // each block so the smoothed power locks to the block's level.
        val blockSec = 30
        var t = 0L
        val targetPowers = listOf(200.0, 400.0, 200.0, 400.0, 200.0)
        val alphas = listOf(0.9f, 0.6f, 0.9f, 0.6f, 0.9f)
        for ((p, a) in targetPowers.zip(alphas)) {
            for (s in 0 until blockSec) {
                calc.addPower(t, p)
                t += 1_000L
            }
            calc.addAlpha(a)
        }
        val est = calc.currentEstimate()
        assertNotNull(est)
        // α at 200 W ≈ 0.9, α at 400 W ≈ 0.6 → 0.75 ≈ 300 W
        assertTrue("estimate should bracket 200..400, got $est", est!! in 200f..400f)
    }
}
