package com.inqulab.heartkaroo.power

import org.junit.Assert.assertEquals
import org.junit.Test

class KilojoulesCalculatorTest {

    @Test
    fun `constant 200 W for one hour is 720 kJ`() {
        val calc = KilojoulesCalculator()
        // 200 W * 3600 s = 720_000 J = 720 kJ
        for (s in 0..3600) calc.add(s * 1000L, 200.0)
        assertEquals(720f, calc.current(), 1f)
    }

    @Test
    fun `varying power integrates correctly`() {
        val calc = KilojoulesCalculator()
        // First 60 s at 100 W, next 60 s at 400 W
        for (s in 0..60) calc.add(s * 1000L, 100.0)
        for (s in 61..120) calc.add(s * 1000L, 400.0)
        // ≈ (100*60 + 250*1 + 400*59 + 250 transitional?) Actually trapezoidal
        // gives: 100*60 + 0.5*(100+400)*1 + 400*59 ≈ 6000 + 250 + 23600 = ~29850 J ≈ 30 kJ
        assertEquals(30f, calc.current(), 1f)
    }

    @Test
    fun `a sensor dropout adds no phantom work`() {
        val calc = KilojoulesCalculator()
        // 600 s at 250 W = 150 kJ, then the power meter goes silent for 10
        // minutes. The first sample after the gap must not integrate across it
        // (that credited a phantom 150 kJ at the pre-gap power).
        for (s in 0..600) calc.add(s * 1000L, 250.0)
        calc.add(1_200_000L, 250.0)
        assertEquals(150f, calc.current(), 1f)
    }

    @Test
    fun `reset zeroes the total`() {
        val calc = KilojoulesCalculator()
        for (s in 0..600) calc.add(s * 1000L, 200.0)
        calc.reset()
        assertEquals(0f, calc.current(), 1e-3f)
    }
}
