package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HrvFlowHoldTest {

    private class RecordingEmitter : Emitter<StreamState> {
        val states = mutableListOf<StreamState>()
        override fun onNext(t: StreamState) { states.add(t) }
        override fun onError(t: Throwable) {}
        override fun onComplete() {}
        override fun setCancellable(cancellable: () -> Unit) {}
        override fun cancel() {}
        fun last(): StreamState = states.last()
    }

    private fun StreamState.value(): Double =
        (this as StreamState.Streaming).dataPoint.singleValue!!

    @Test
    fun `disconnected shows Searching`() = runTest {
        val source = MutableStateFlow<Float?>(null)
        val connected = MutableStateFlow(false)
        val emitter = RecordingEmitter()
        streamFloatWithHold(source, connected, "x", emitter, holdMs = 8_000L, scope = backgroundScope)
        advanceTimeBy(100)
        assertEquals(StreamState.Searching, emitter.last())
    }

    @Test
    fun `connected with value streams it`() = runTest {
        val source = MutableStateFlow<Float?>(null)
        val connected = MutableStateFlow(false)
        val emitter = RecordingEmitter()
        streamFloatWithHold(source, connected, "x", emitter, holdMs = 8_000L, scope = backgroundScope)
        connected.value = true
        source.value = 42f
        advanceTimeBy(100)
        assertTrue(emitter.last() is StreamState.Streaming)
        assertEquals(42.0, emitter.last().value(), 1e-6)
    }

    @Test
    fun `connected but no value yet is Idle not Searching`() = runTest {
        val source = MutableStateFlow<Float?>(null)
        val connected = MutableStateFlow(true)
        val emitter = RecordingEmitter()
        streamFloatWithHold(source, connected, "x", emitter, holdMs = 8_000L, scope = backgroundScope)
        advanceTimeBy(100)
        assertEquals(StreamState.Idle, emitter.last())
    }

    @Test
    fun `brief gap holds last value then goes Idle`() = runTest {
        val source = MutableStateFlow<Float?>(null)
        val connected = MutableStateFlow(true)
        val emitter = RecordingEmitter()
        streamFloatWithHold(source, connected, "x", emitter, holdMs = 8_000L, scope = backgroundScope)
        source.value = 55f
        advanceTimeBy(100)
        // gap begins
        source.value = null
        advanceTimeBy(1_000) // still within the hold window
        assertTrue("should hold the last value", emitter.last() is StreamState.Streaming)
        assertEquals(55.0, emitter.last().value(), 1e-6)
        advanceTimeBy(8_000) // past the hold window
        assertEquals(StreamState.Idle, emitter.last())
    }

    @Test
    fun `value returning before hold expires keeps streaming`() = runTest {
        val source = MutableStateFlow<Float?>(null)
        val connected = MutableStateFlow(true)
        val emitter = RecordingEmitter()
        streamFloatWithHold(source, connected, "x", emitter, holdMs = 8_000L, scope = backgroundScope)
        source.value = 10f
        advanceTimeBy(100)
        source.value = null
        advanceTimeBy(2_000)
        source.value = 20f
        advanceTimeBy(10_000) // well past the original hold window
        assertTrue(emitter.last() is StreamState.Streaming)
        assertEquals(20.0, emitter.last().value(), 1e-6)
    }

    @Test
    fun `disconnect while holding shows Searching`() = runTest {
        val source = MutableStateFlow<Float?>(null)
        val connected = MutableStateFlow(true)
        val emitter = RecordingEmitter()
        streamFloatWithHold(source, connected, "x", emitter, holdMs = 8_000L, scope = backgroundScope)
        source.value = 10f
        advanceTimeBy(100)
        source.value = null
        advanceTimeBy(1_000)
        connected.value = false // strap drops mid-hold
        advanceTimeBy(100)
        assertEquals(StreamState.Searching, emitter.last())
    }
}
