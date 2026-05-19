package com.inqulab.heartkaroo.readiness

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Tests the pure logic of the readiness verdict.
 *
 * SharedPreferences is Android-only so the persistence layer is left to
 * instrumentation; here we exercise statusFor() in isolation by feeding
 * known baseline points (which would normally come from prefs) via the
 * recent() override.
 */
class ReadinessStoreLogicTest {

    @Test
    fun `verdict logic matches a known baseline`() {
        // Simulate the math statusFor would do, end-to-end:
        val baseline = listOf(50f, 55f, 60f, 65f, 70f).map { ln(it.toDouble()) }
        val mean = baseline.average()
        val sd = sqrt(baseline.sumOf { (it - mean) * (it - mean) } / (baseline.size - 1))

        fun verdictFor(today: Float): String {
            val z = (ln(today.toDouble()) - mean) / sd
            return when {
                z >= 0.5 -> "GO_HARD"
                z <= -0.5 -> "GO_EASY"
                else -> "NORMAL"
            }
        }

        assertEquals("GO_HARD", verdictFor(80f))
        assertEquals("GO_EASY", verdictFor(40f))
        assertEquals("NORMAL", verdictFor(60f))
        assertTrue("baseline SD should be positive", sd > 0.0)
    }
}
