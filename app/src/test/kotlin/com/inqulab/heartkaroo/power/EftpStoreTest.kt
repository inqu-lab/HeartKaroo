package com.inqulab.heartkaroo.power

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the SharedPreferences-backed EftpStore: persistence,
 * the 42-day window, the window-max rolling estimate, validation and clear().
 */
@RunWith(RobolectricTestRunner::class)
class EftpStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = 1_700_000_000_000L
    private val dayMs = 24L * 60 * 60 * 1000

    private fun freshStore(): EftpStore {
        context.getSharedPreferences("eftp", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return EftpStore(context)
    }

    @Test
    fun `rollingBest is null with no history`() {
        assertNull(freshStore().rollingBest(now))
    }

    @Test
    fun `entries round-trip and rollingBest is the window max`() {
        val store = freshStore()
        store.record(now - dayMs, 250f)
        store.record(now, 230f)

        val reread = EftpStore(context)
        assertEquals(2, reread.entries(now).size)
        // The best ride in the window wins — an easier later ride doesn't drag it down.
        assertEquals(250f, reread.rollingBest(now)!!, 1e-3f)
    }

    @Test
    fun `non-positive watts are rejected`() {
        val store = freshStore()
        store.record(now, 0f)
        store.record(now, -5f)

        assertTrue(store.entries(now).isEmpty())
        assertNull(store.rollingBest(now))
    }

    @Test
    fun `entries older than the 42-day window are dropped`() {
        val store = freshStore()
        store.record(now - 43 * dayMs, 999f)
        store.record(now, 215f)

        val kept = store.entries(now)
        assertEquals(1, kept.size)
        assertEquals(215f, store.rollingBest(now)!!, 1e-3f)
    }

    @Test
    fun `clear wipes persisted history`() {
        val store = freshStore()
        store.record(now, 210f)
        store.clear()

        assertTrue(EftpStore(context).entries(now).isEmpty())
    }

    @Test
    fun `malformed persisted entries are ignored`() {
        freshStore()
        context.getSharedPreferences("eftp", Context.MODE_PRIVATE)
            .edit()
            .putString("ride_finals", "junk,${now}:210.0,${now}:bad")
            .commit()

        val kept = EftpStore(context).entries(now)
        assertEquals(1, kept.size)
        assertEquals(210f, kept.first().eftpW, 1e-4f)
    }
}
