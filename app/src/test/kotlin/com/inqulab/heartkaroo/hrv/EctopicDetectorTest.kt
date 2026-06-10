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
    fun `a sustained baseline shift re-syncs instead of flagging every beat`() {
        val det = EctopicDetector()
        // Steady 800 ms, then the stream resumes after a surge at 620 ms
        // (>20 % off the stale reference). Without re-sync every post-shift
        // beat stayed flagged against 800 ms — a heart-rate-sized ~52/min
        // "ectopic" rate for minutes. Only the first few may flag.
        repeat(40) { det.addInterval(800) }
        repeat(60) { det.addInterval(620) }
        val rate = det.getEventsPerMin()
        assertNotNull(rate)
        assertTrue("expected re-synced low rate, got $rate", rate!! < 5f)
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
