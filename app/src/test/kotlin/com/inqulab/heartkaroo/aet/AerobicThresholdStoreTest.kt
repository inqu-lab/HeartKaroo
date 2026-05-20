package com.inqulab.heartkaroo.aet

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the real SharedPreferences-backed AerobicThresholdStore:
 * persistence, the 90-day window, input validation, and clear().
 */
@RunWith(RobolectricTestRunner::class)
class AerobicThresholdStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun freshStore(): AerobicThresholdStore {
        context.getSharedPreferences("aet", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return AerobicThresholdStore(context)
    }

    @Test
    fun `rollingEstimate is null with no history`() {
        assertNull(freshStore().rollingEstimate(now))
    }

    @Test
    fun `entries round-trip and rollingEstimate is sample-count weighted`() {
        val store = freshStore()
        store.record(now - dayMs, 200f, sampleCount = 100)
        store.record(now, 230f, sampleCount = 300)

        val reread = AerobicThresholdStore(context)
        assertEquals(2, reread.entries(now).size)
        // (200*100 + 230*300) / 400 = 222.5
        assertEquals(222.5f, reread.rollingEstimate(now)!!, 1e-3f)
    }

    @Test
    fun `non-positive watts or sample counts are rejected`() {
        val store = freshStore()
        store.record(now, 0f, sampleCount = 100)
        store.record(now, 210f, sampleCount = 0)
        store.record(now, -5f, sampleCount = 50)

        assertTrue(store.entries(now).isEmpty())
        assertNull(store.rollingEstimate(now))
    }

    @Test
    fun `entries older than the 90-day window are dropped`() {
        val store = freshStore()
        store.record(now - 91 * dayMs, 999f, sampleCount = 100)
        store.record(now, 215f, sampleCount = 100)

        val kept = store.entries(now)
        assertEquals(1, kept.size)
        assertEquals(215f, kept.first().aetW, 1e-4f)
    }

    @Test
    fun `clear wipes persisted history`() {
        val store = freshStore()
        store.record(now, 210f, sampleCount = 100)
        store.clear()

        assertTrue(AerobicThresholdStore(context).entries(now).isEmpty())
    }

    @Test
    fun `malformed persisted entries are ignored`() {
        freshStore()
        context.getSharedPreferences("aet", Context.MODE_PRIVATE)
            .edit()
            .putString("ride_estimates", "junk,${now}:210.0:100,${now}:bad:100")
            .commit()

        val kept = AerobicThresholdStore(context).entries(now)
        assertEquals(1, kept.size)
        assertEquals(210f, kept.first().aetW, 1e-4f)
    }
}
