package com.inqulab.heartkaroo

import io.hammerhead.karooext.models.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the pure field-selection logic extracted from the extension's
 * after-ride session-message writer: which metrics are written, which are
 * omitted when unresolved, and the W′-min / matches-burned pairing.
 * (Robolectric only so the KarooExtension subclass loads cleanly.)
 */
@RunWith(RobolectricTestRunner::class)
class SessionSummaryFieldsTest {

    private fun snapshot(
        aet: Float? = null,
        vt2: Float? = null,
        wPrimeMinJ: Double? = null,
        matchesBurned: Int = 0,
        dfaAerobicS: Double = 0.0,
        dfaThresholdS: Double = 0.0,
        dfaHardS: Double = 0.0,
        cardiacPopMin: Float? = null,
        quadrantPct: DoubleArray? = null,
    ) = HeartKarooExtension.RideSummarySnapshot(
        aet, vt2, wPrimeMinJ, matchesBurned,
        dfaAerobicS, dfaThresholdS, dfaHardS, cardiacPopMin, quadrantPct,
    )

    private fun List<FieldValue>.byName(): Map<String, Double> =
        associate { it.developerField!!.fieldName to it.value }

    @Test
    fun `unresolved metrics are omitted but DFA zone seconds are always written`() {
        val byName = HeartKarooExtension.sessionSummaryFields(snapshot()).byName()
        assertEquals(
            setOf("dfa_a1_aerobic_s", "dfa_a1_threshold_s", "dfa_a1_hard_s"),
            byName.keys,
        )
    }

    @Test
    fun `matches burned only rides along with W-prime min`() {
        // No W′ data -> neither field, even with a non-zero count.
        assertFalse(
            HeartKarooExtension.sessionSummaryFields(snapshot(matchesBurned = 3))
                .byName().containsKey("matches_burned"),
        )
        // W′ present -> both appear together.
        val byName = HeartKarooExtension
            .sessionSummaryFields(snapshot(wPrimeMinJ = 1234.0, matchesBurned = 3))
            .byName()
        assertEquals(1234.0, byName.getValue("w_prime_min"), 1e-9)
        assertEquals(3.0, byName.getValue("matches_burned"), 1e-9)
    }

    @Test
    fun `a full snapshot writes every field with the right values`() {
        val fields = HeartKarooExtension.sessionSummaryFields(
            snapshot(
                aet = 210f,
                vt2 = 260f,
                wPrimeMinJ = 500.0,
                matchesBurned = 2,
                dfaAerobicS = 600.0,
                dfaThresholdS = 300.0,
                dfaHardS = 120.0,
                cardiacPopMin = 18f,
                quadrantPct = doubleArrayOf(40.0, 30.0, 20.0, 10.0),
            ),
        )
        val byName = fields.byName()
        assertEquals(12, fields.size)
        assertEquals(210.0, byName.getValue("aet_estimate"), 1e-6)
        assertEquals(260.0, byName.getValue("vt2_estimate"), 1e-6)
        assertEquals(500.0, byName.getValue("w_prime_min"), 1e-9)
        assertEquals(2.0, byName.getValue("matches_burned"), 1e-9)
        assertEquals(600.0, byName.getValue("dfa_a1_aerobic_s"), 1e-9)
        assertEquals(18.0, byName.getValue("cardiac_pop_min"), 1e-6)
        assertEquals(40.0, byName.getValue("quadrant1_pct"), 1e-9)
        assertEquals(10.0, byName.getValue("quadrant4_pct"), 1e-9)
    }
}
