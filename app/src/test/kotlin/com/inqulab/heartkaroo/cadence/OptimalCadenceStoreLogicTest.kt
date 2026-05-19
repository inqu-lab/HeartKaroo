package com.inqulab.heartkaroo.cadence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OptimalCadenceStoreLogicTest {

    @Test
    fun `empty entries yield null`() {
        assertNull(OptimalCadenceStore.weightedMean(emptyList()))
    }

    @Test
    fun `entries with zero samples are skipped`() {
        val mean = OptimalCadenceStore.weightedMean(
            listOf(OptimalCadenceStore.Entry(0L, 90f, 0)),
        )
        assertNull(mean)
    }

    @Test
    fun `weighted mean weights by sample count`() {
        // 85 RPM (weight 100) + 95 RPM (weight 300) → weighted mean = (85*100 + 95*300)/400 = 92.5
        val mean = OptimalCadenceStore.weightedMean(
            listOf(
                OptimalCadenceStore.Entry(0L, 85f, 100),
                OptimalCadenceStore.Entry(1L, 95f, 300),
            ),
        )
        assertNotNull(mean)
        assertEquals(92.5f, mean!!, 1e-3f)
    }
}
