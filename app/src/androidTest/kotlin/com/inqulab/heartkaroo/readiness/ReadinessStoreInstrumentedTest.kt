package com.inqulab.heartkaroo.readiness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device counterpart to the Robolectric ReadinessStoreTest: exercises the
 * same persistence + baseline logic against the *real* Android framework
 * (real SharedPreferences, not a shadow) on an emulator/device. This is the
 * smoke test the `connectedDebugAndroidTest` CI job runs.
 */
@RunWith(AndroidJUnit4::class)
class ReadinessStoreInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("readiness", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun recordedReadingsRoundTripThroughDevicePrefs() {
        val store = ReadinessStore(context)
        store.record(now - 2 * dayMs, 48f)
        store.record(now - 1 * dayMs, 52f)
        store.record(now, 60f)

        val reread = ReadinessStore(context).recent(now)
        assertEquals(3, reread.size)
        assertEquals(60f, reread.last().rmssdMs, 1e-4f)
    }

    @Test
    fun readingsOlderThanTheWindowAreDropped() {
        val store = ReadinessStore(context)
        store.record(now - 8 * dayMs, 99f)
        store.record(now, 55f)

        val kept = store.recent(now)
        assertEquals(1, kept.size)
        assertEquals(55f, kept.first().rmssdMs, 1e-4f)
    }

    @Test
    fun statusForFollowsTheZScoreOfAPersistedBaseline() {
        val store = ReadinessStore(context)
        listOf(50f, 55f, 60f, 65f, 70f).forEachIndexed { i, v ->
            store.record(now - (5 - i) * dayMs, v)
        }

        assertEquals(ReadinessStore.Verdict.GO_HARD, store.statusFor(80f, now).label)
        assertEquals(ReadinessStore.Verdict.GO_EASY, store.statusFor(40f, now).label)
        assertEquals(ReadinessStore.Verdict.NORMAL, store.statusFor(60f, now).label)
        assertTrue(store.statusFor(60f, now).baselineLnSd > 0f)
    }
}
