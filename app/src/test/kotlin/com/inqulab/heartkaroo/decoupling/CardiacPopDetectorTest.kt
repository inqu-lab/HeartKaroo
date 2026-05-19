package com.inqulab.heartkaroo.decoupling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CardiacPopDetectorTest {

    @Test
    fun `returns null while decoupling is below threshold`() {
        val det = CardiacPopDetector(thresholdPct = 5.0, confirmMs = 5_000L)
        var t = 0L
        for (i in 0 until 60) { det.add(t, 2.0); t += 1_000L }
        assertNull(det.getPopMinutes())
    }

    @Test
    fun `latches when threshold sustained for confirm period`() {
        val det = CardiacPopDetector(thresholdPct = 5.0, confirmMs = 30_000L)
        var t = 0L
        // 10 min at 2% then ramp up to 6% and hold
        for (i in 0 until 600) { det.add(t, 2.0); t += 1_000L }
        for (i in 0 until 60) { det.add(t, 6.0); t += 1_000L }
        val mins = det.getPopMinutes()
        assertTrue("expected pop near 10 min, got $mins", mins != null && mins in 9.9f..10.1f)
    }

    @Test
    fun `single noisy spike does not latch`() {
        val det = CardiacPopDetector(thresholdPct = 5.0, confirmMs = 30_000L)
        var t = 0L
        for (i in 0 until 120) { det.add(t, 2.0); t += 1_000L }
        // 10 s spike — too short to confirm
        for (i in 0 until 10) { det.add(t, 7.0); t += 1_000L }
        for (i in 0 until 120) { det.add(t, 2.0); t += 1_000L }
        assertNull(det.getPopMinutes())
    }

    @Test
    fun `pop time stays constant once latched`() {
        val det = CardiacPopDetector(thresholdPct = 5.0, confirmMs = 30_000L)
        var t = 0L
        for (i in 0 until 60) { det.add(t, 6.0); t += 1_000L }
        val firstReading = det.getPopMinutes()
        for (i in 0 until 600) { det.add(t, 12.0); t += 1_000L }
        val laterReading = det.getPopMinutes()
        assertEquals(firstReading, laterReading)
    }

    @Test
    fun `reset clears state`() {
        val det = CardiacPopDetector()
        var t = 0L
        for (i in 0 until 120) { det.add(t, 10.0); t += 1_000L }
        det.reset()
        assertNull(det.getPopMinutes())
    }

    @Test
    fun `null decoupling samples are ignored without breaking confirmation`() {
        val det = CardiacPopDetector(thresholdPct = 5.0, confirmMs = 10_000L)
        var t = 0L
        det.add(t, 6.0); t += 5_000L
        det.add(t, null); t += 1_000L
        det.add(t, 6.0); t += 5_000L
        // 5 s + 5 s above threshold, with a null gap — should still confirm
        val mins = det.getPopMinutes()
        assertTrue("expected latch with null gap, got $mins", mins != null)
    }
}
