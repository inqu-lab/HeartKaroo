package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class RespiratoryRateCalculatorTest {

    @Test
    fun `returns null before twelve intervals collected`() {
        val calc = RespiratoryRateCalculator()
        repeat(8) { calc.addInterval(800) }
        assertNull(calc.getBreathsPerMin())
    }

    @Test
    fun `constant RR is non-oscillatory and returns null`() {
        val calc = RespiratoryRateCalculator()
        repeat(30) { calc.addInterval(800) }
        // No zero crossings → 0 breaths → below clamp → null
        assertNull(calc.getBreathsPerMin())
    }

    @Test
    fun `synthetic RSA at known frequency is recovered approximately`() {
        // 30 beats at avg 1000 ms = 30 s window. Encode a 0.25 Hz oscillation
        // (15 breaths/min) into the RR series.
        val calc = RespiratoryRateCalculator(windowSize = 30)
        val breathHz = 0.25
        val avgRr = 1000.0
        var t = 0.0
        for (i in 0 until 30) {
            val rr = avgRr + 100.0 * sin(2 * PI * breathHz * t)
            calc.addInterval(rr.toInt())
            t += rr / 1000.0
        }
        val brpm = calc.getBreathsPerMin()
        assertNotNull(brpm)
        assertTrue("expected ~15 brpm, got $brpm", brpm!! in 10f..20f)
    }
}
