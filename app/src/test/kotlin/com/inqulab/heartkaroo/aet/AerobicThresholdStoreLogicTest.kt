package com.inqulab.heartkaroo.aet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the pure weighted-mean logic of AerobicThresholdStore in
 * isolation from SharedPreferences (which is Android-only).
 */
class AerobicThresholdStoreLogicTest {

    @Test
    fun `empty entries yield null`() {
        assertNull(AerobicThresholdStore.weightedMean(emptyList()))
    }

    @Test
    fun `entries with zero samples are skipped`() {
        val mean = AerobicThresholdStore.weightedMean(
            listOf(AerobicThresholdStore.Entry(0L, 200f, 0)),
        )
        assertNull(mean)
    }

    @Test
    fun `weighted mean weights by sample count`() {
        // 100 W (weight 100) + 200 W (weight 300) → weighted mean = (100*100 + 200*300)/400 = 175
        val mean = AerobicThresholdStore.weightedMean(
            listOf(
                AerobicThresholdStore.Entry(0L, 100f, 100),
                AerobicThresholdStore.Entry(1L, 200f, 300),
            ),
        )
        assertNotNull(mean)
        assertEquals(175f, mean!!, 1e-3f)
    }

    @Test
    fun `single entry round trips`() {
        val mean = AerobicThresholdStore.weightedMean(
            listOf(AerobicThresholdStore.Entry(0L, 250f, 500)),
        )
        assertEquals(250f, mean!!, 1e-3f)
    }
}
