package com.inqulab.heartkaroo.karoo

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * streamFloatState is the shared bridge every RidePowerEngine-backed data
 * field uses: a Float? source maps to Searching (null) or Streaming (value).
 * It collects on its own Dispatchers.IO scope, so the test pushes values to a
 * real StateFlow and briefly polls the recording emitter.
 */
class KarooStreamExtTest {

    private class RecordingEmitter : Emitter<StreamState> {
        val states = mutableListOf<StreamState>()
        override fun onNext(t: StreamState) { synchronized(states) { states.add(t) } }
        override fun onError(t: Throwable) {}
        override fun onComplete() {}
        override fun setCancellable(cancellable: () -> Unit) {}
        override fun cancel() {}
        fun lastOrNull(): StreamState? = synchronized(states) { states.lastOrNull() }
        fun size(): Int = synchronized(states) { states.size }
    }

    private fun StreamState.value(): Double =
        (this as StreamState.Streaming).dataPoint.singleValue!!

    private fun awaitUntil(timeoutMs: Long = 2_000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(5)
        }
        fail("condition not met within ${timeoutMs}ms")
    }

    @Test
    fun `null maps to Searching and a value maps to Streaming`() {
        val source = MutableStateFlow<Float?>(null)
        val emitter = RecordingEmitter()
        val cancel = streamFloatState(source, "test_field", emitter)
        try {
            awaitUntil { emitter.lastOrNull() == StreamState.Searching }

            source.value = 42f
            awaitUntil { emitter.lastOrNull() is StreamState.Streaming }
            assertEquals(42.0, emitter.lastOrNull()!!.value(), 1e-9)

            source.value = null
            awaitUntil { emitter.lastOrNull() == StreamState.Searching }
        } finally {
            cancel()
        }
    }

    @Test
    fun `cancel stops further emissions`() {
        val source = MutableStateFlow<Float?>(1f)
        val emitter = RecordingEmitter()
        val cancel = streamFloatState(source, "x", emitter)
        awaitUntil { emitter.lastOrNull() is StreamState.Streaming }

        cancel()
        Thread.sleep(50)
        val countAfterCancel = emitter.size()
        source.value = 999f
        Thread.sleep(50)

        assertEquals("no emissions after cancel", countAfterCancel, emitter.size())
    }
}
