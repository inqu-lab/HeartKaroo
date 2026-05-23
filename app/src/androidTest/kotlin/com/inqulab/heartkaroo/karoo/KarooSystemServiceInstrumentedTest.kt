package com.inqulab.heartkaroo.karoo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inqulab.heartkaroo.awaitFlow
import com.inqulab.heartkaroo.connectBlocking
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on the Karoo (`./gradlew connectedDebugAndroidTest`). Verifies the real
 * KarooSystemService binder handshake and the stream consumer pipeline — the
 * exact things that can't run under Robolectric (its bindService delivers a
 * null binder, which KarooSystemService rejects).
 *
 * `connectsToTheKarooSystem` requires only a Karoo. The stream test additionally
 * needs the data pipeline up and is skipped otherwise.
 */
@RunWith(AndroidJUnit4::class)
class KarooSystemServiceInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var karoo: KarooSystemService? = null

    @After
    fun tearDown() {
        karoo?.disconnect()
    }

    @Test
    fun connectsToTheKarooSystem() {
        val k = KarooSystemService(context).also { karoo = it }
        assertTrue("KarooSystemService should connect on a Karoo within 8 s", k.connectBlocking())
        assertTrue("connected flag should be set after the callback", k.connected)
    }

    @Test
    fun powerStreamDeliversAState() {
        val k = KarooSystemService(context).also { karoo = it }
        assumeTrue("needs a Karoo connection", k.connectBlocking())

        // Once connected, the stream emits at least a state (Searching/Idle/
        // Streaming) even before a power meter is paired.
        val state: StreamState? = awaitFlow(8_000, k.streamDataFlow(DataType.Type.POWER))
        assertNotNull("the power stream should deliver a StreamState once connected", state)
    }
}
