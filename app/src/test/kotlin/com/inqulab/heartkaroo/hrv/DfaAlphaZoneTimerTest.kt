package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Test

class DfaAlphaZoneTimerTest {

    @Test
    fun `attributes elapsed time to the band of the held value`() {
        val t = DfaAlphaZoneTimer()
        t.add(0, 0.9)        // aerobic held for next interval
        t.add(1_000, 0.6)    // +1s aerobic; threshold held next
        t.add(2_000, 0.4)    // +1s threshold; hard held next
        t.add(3_000, 0.4)    // +1s hard
        assertEquals(1.0, t.aerobicSeconds(), 1e-9)
        assertEquals(1.0, t.thresholdSeconds(), 1e-9)
        assertEquals(1.0, t.hardSeconds(), 1e-9)
    }

    @Test
    fun `band boundaries are inclusive at the lower edge`() {
        val t = DfaAlphaZoneTimer()
        t.add(0, 0.75)       // exactly 0.75 -> aerobic
        t.add(1_000, 0.50)   // +1s aerobic; exactly 0.50 -> threshold
        t.add(2_000, 0.50)   // +1s threshold
        assertEquals(1.0, t.aerobicSeconds(), 1e-9)
        assertEquals(1.0, t.thresholdSeconds(), 1e-9)
        assertEquals(0.0, t.hardSeconds(), 1e-9)
    }

    @Test
    fun `gaps longer than the cap are not attributed`() {
        val t = DfaAlphaZoneTimer(maxGapMs = 10_000L)
        t.add(0, 0.9)
        t.add(20_000, 0.9)   // 20s gap > cap -> dropped
        assertEquals(0.0, t.aerobicSeconds(), 1e-9)
        assertEquals(0.0, t.thresholdSeconds(), 1e-9)
        assertEquals(0.0, t.hardSeconds(), 1e-9)
    }
}
