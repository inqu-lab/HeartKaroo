package com.inqulab.heartkaroo.readiness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the real SharedPreferences-backed ReadinessStore —
 * the persistence + eviction the pure-logic test deliberately skipped.
 */
@RunWith(RobolectricTestRunner::class)
class ReadinessStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun freshStore(): ReadinessStore {
        // Each test starts from a clean prefs file.
        context.getSharedPreferences("readiness", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return ReadinessStore(context)
    }

    @Test
    fun `recent is empty before anything is recorded`() {
        assertTrue(freshStore().recent(now).isEmpty())
    }

    @Test
    fun `recorded readings round-trip through SharedPreferences`() {
        val store = freshStore()
        store.record(now - 2 * dayMs, 48f)
        store.record(now - 1 * dayMs, 52f)
        store.record(now, 60f)

        // A brand-new instance reads the same persisted prefs back.
        val reread = ReadinessStore(context).recent(now)
        assertEquals(3, reread.size)
        assertEquals(60f, reread.last().rmssdMs, 1e-4f)
    }

    @Test
    fun `readings older than the 7-day window are dropped`() {
        val store = freshStore()
        store.record(now - 8 * dayMs, 99f) // outside the window
        store.record(now, 55f)             // inside

        val kept = store.recent(now)
        assertEquals(1, kept.size)
        assertEquals(55f, kept.first().rmssdMs, 1e-4f)
    }

    @Test
    fun `statusFor reports NO_BASELINE with fewer than three readings`() {
        val store = freshStore()
        store.record(now - dayMs, 50f)
        store.record(now, 55f)

        val status = store.statusFor(60f, now)
        assertEquals(ReadinessStore.Verdict.NO_BASELINE, status.label)
    }

    @Test
    fun `statusFor verdicts follow the z-score of a persisted baseline`() {
        val store = freshStore()
        listOf(50f, 55f, 60f, 65f, 70f).forEachIndexed { i, v ->
            store.record(now - (5 - i) * dayMs, v)
        }

        assertEquals(ReadinessStore.Verdict.GO_HARD, store.statusFor(80f, now).label)
        assertEquals(ReadinessStore.Verdict.GO_EASY, store.statusFor(40f, now).label)
        assertEquals(ReadinessStore.Verdict.NORMAL, store.statusFor(60f, now).label)

        val status = store.statusFor(60f, now)
        assertTrue("baseline SD should be positive", status.baselineLnSd > 0f)
    }

    @Test
    fun `malformed persisted entries are ignored, not crashed on`() {
        freshStore()
        // Simulate a corrupted prefs value written by an older/buggy build.
        context.getSharedPreferences("readiness", Context.MODE_PRIVATE)
            .edit()
            .putString("rmssd_readings", "garbage,${now}:55.0,${now}:notanumber")
            .commit()

        val kept = ReadinessStore(context).recent(now)
        assertEquals(1, kept.size)
        assertEquals(55f, kept.first().rmssdMs, 1e-4f)
    }
}
