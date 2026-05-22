package com.inqulab.heartkaroo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrapBatteryAlerterTest {

    @Test
    fun `alerts once when level first drops to the threshold`() {
        val alerter = StrapBatteryAlerter(warnAtPct = 15, rearmAbovePct = 25)
        assertFalse(alerter.shouldAlert(40))
        assertFalse(alerter.shouldAlert(16))
        assertTrue(alerter.shouldAlert(15))
        // Already warned: no repeat while it lingers low.
        assertFalse(alerter.shouldAlert(15))
        assertFalse(alerter.shouldAlert(8))
    }

    @Test
    fun `re-arms only after recovering above the rearm threshold`() {
        val alerter = StrapBatteryAlerter(warnAtPct = 15, rearmAbovePct = 25)
        assertTrue(alerter.shouldAlert(10))
        // 25 is not above the rearm threshold -> stays armed-down.
        assertFalse(alerter.shouldAlert(25))
        assertFalse(alerter.shouldAlert(10))
        // 26 recovers -> re-arm, so the next drop alerts again.
        assertFalse(alerter.shouldAlert(26))
        assertTrue(alerter.shouldAlert(15))
    }

    @Test
    fun `persist gate requires an estimate and enough samples`() {
        assertFalse(shouldPersistRollingFinal(null, 1_000, 60))
        assertFalse(shouldPersistRollingFinal(220f, 59, 60))
        assertTrue(shouldPersistRollingFinal(220f, 60, 60))
        assertTrue(shouldPersistRollingFinal(220f, 300, 60))
    }
}
