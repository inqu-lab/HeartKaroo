package com.inqulab.heartkaroo.hrv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RrArtifactCorrectorTest {

    @Test
    fun `first interval is always accepted`() {
        val c = RrArtifactCorrector()
        assertEquals(800, c.accept(800))
    }

    @Test
    fun `steady rhythm passes through unchanged`() {
        val c = RrArtifactCorrector()
        repeat(20) { assertEquals(800, c.accept(800)) }
    }

    @Test
    fun `small physiological variation is kept`() {
        val c = RrArtifactCorrector()
        c.accept(800)
        // ±10% beat-to-beat is normal HRV and must not be rejected.
        assertNotNull(c.accept(880))
        assertNotNull(c.accept(720))
    }

    @Test
    fun `single ectopic spike is dropped`() {
        val c = RrArtifactCorrector(thresholdFrac = 0.25)
        repeat(5) { c.accept(800) }
        assertNull("a 50% short beat is an artifact", c.accept(400))
        // and the rhythm continues to be accepted afterwards
        assertEquals(800, c.accept(800))
    }

    @Test
    fun `missed beat producing a double-length RR is dropped`() {
        val c = RrArtifactCorrector(thresholdFrac = 0.25)
        repeat(5) { c.accept(800) }
        assertNull(c.accept(1600))
    }

    @Test
    fun `sustained baseline shift re-syncs instead of starving`() {
        val c = RrArtifactCorrector(thresholdFrac = 0.25, maxConsecutiveRejections = 4)
        repeat(5) { c.accept(1000) }
        // Rider surges: RR drops to 600 ms and stays there. The first few are
        // rejected as too far from the 1000 ms baseline, but the corrector must
        // adopt the new level rather than rejecting forever.
        val results = (1..6).map { c.accept(600) }
        assertNotNull("baseline must re-sync after a sustained shift", results.last())
        assertEquals(600, c.accept(600))
    }

    @Test
    fun `reset clears the reference`() {
        val c = RrArtifactCorrector()
        repeat(5) { c.accept(1000) }
        c.reset()
        // After reset the next value seeds a fresh reference and is accepted.
        assertEquals(500, c.accept(500))
    }

    @Test
    fun `artifact rate is zero for a clean rhythm`() {
        val c = RrArtifactCorrector()
        repeat(30) { c.accept(800) }
        assertEquals(0.0, c.recentArtifactRate(), 1e-9)
    }

    @Test
    fun `artifact rate rises with rejected beats`() {
        val c = RrArtifactCorrector(thresholdFrac = 0.25)
        repeat(20) { c.accept(800) }
        // Two isolated ectopic spikes, each surrounded by clean beats so they
        // are rejected rather than treated as a baseline shift.
        c.accept(400); c.accept(800)
        c.accept(1300); c.accept(800)
        val rate = c.recentArtifactRate()
        assertTrue("expected a small positive artifact rate, got $rate", rate > 0.0 && rate < 0.2)
    }

    @Test
    fun `reset clears the artifact rate`() {
        val c = RrArtifactCorrector(thresholdFrac = 0.25)
        repeat(5) { c.accept(800) }
        c.accept(400)
        c.reset()
        assertEquals(0.0, c.recentArtifactRate(), 1e-9)
    }
}
