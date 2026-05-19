package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EctopicDetectorTest {

    @Test
    fun `returns null before thirty beats collected`() {
        val det = EctopicDetector()
        repeat(20) { det.addInterval(800) }
        assertNull(det.getEventsPerMin())
    }

    @Test
    fun `steady rhythm yields zero events`() {
        val det = EctopicDetector()
        repeat(60) { det.addInterval(800) }
        assertEquals(0f, det.getEventsPerMin()!!, 1e-3f)
    }

    @Test
    fun `large deviations are flagged`() {
        val det = EctopicDetector(windowSize = 60, thresholdFrac = 0.20)
        // 30 normal beats at 800 ms, then a 1100 ms beat (37.5% jump) and back
        repeat(30) { det.addInterval(800) }
        det.addInterval(1100)
        repeat(29) { det.addInterval(800) }
        val rate = det.getEventsPerMin()
        assertNotNull(rate)
        // Window total ≈ 60 beats × ~810 ms ≈ 48.6 s → 1 event ≈ 1.23/min
        assertTrue("expected ~1/min, got $rate", rate!! > 0.5f && rate < 3f)
    }

    @Test
    fun `small deviations are not flagged`() {
        val det = EctopicDetector(windowSize = 60, thresholdFrac = 0.20)
        // 800 → 920 is 15% jump → below threshold → not flagged
        repeat(30) { det.addInterval(800) }
        det.addInterval(920)
        repeat(29) { det.addInterval(800) }
        assertEquals(0f, det.getEventsPerMin()!!, 1e-3f)
    }

    @Test
    fun `reset clears state`() {
        val det = EctopicDetector()
        repeat(60) { det.addInterval(800) }
        det.reset()
        assertNull(det.getEventsPerMin())
    }
}
