package com.inqulab.heartkaroo.karoo

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamCollectorsTest {

    private class RecordingEmitter : Emitter<StreamState> {
        val states = mutableListOf<StreamState>()
        override fun onNext(t: StreamState) { states.add(t) }
        override fun onError(t: Throwable) {}
        override fun onComplete() {}
        override fun setCancellable(cancellable: () -> Unit) {}
        override fun cancel() {}
        fun last() = states.last()
    }

    private fun point(value: Double): StreamState =
        StreamState.Streaming(DataPoint("x", mapOf("v" to value)))

    private fun StreamState.value(): Double =
        (this as StreamState.Streaming).dataPoint.values.values.first()

    private fun newFlow() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)

    // --- single source ---

    @Test
    fun `single - null compute maps to Searching, value to Streaming`() = runTest(UnconfinedTestDispatcher()) {
        val emitter = RecordingEmitter()
        backgroundScope.collectStreamMetric(
            flowOf(point(50.0), point(200.0)), "id", "f", emitter, now = { 7L },
        ) { _, v -> if (v >= 100.0) v else null }
        runCurrent()

        assertEquals(2, emitter.states.size)
        assertEquals(StreamState.Searching, emitter.states[0])
        assertEquals(200.0, emitter.states[1].value(), 1e-9)
    }

    @Test
    fun `single - non-streaming input is skipped and timestamp is injected`() = runTest(UnconfinedTestDispatcher()) {
        val emitter = RecordingEmitter()
        val seenTimes = mutableListOf<Long>()
        backgroundScope.collectStreamMetric(
            flowOf(StreamState.Searching, StreamState.Idle, point(200.0)), "id", "f", emitter, now = { 42L },
        ) { t, v -> seenTimes.add(t); v }
        runCurrent()

        assertEquals(1, emitter.states.size)
        assertEquals(200.0, emitter.states[0].value(), 1e-9)
        assertEquals(listOf(42L), seenTimes)
    }

    // --- two sources ---

    @Test
    fun `dual - compute receives both values, and null for a missing stream`() = runTest(UnconfinedTestDispatcher()) {
        val a = newFlow()
        val b = newFlow()
        val emitter = RecordingEmitter()
        val seen = mutableListOf<Pair<Double?, Double?>>()
        backgroundScope.collectStreamMetric2(a, b, "id", "f", emitter) { _, x, y ->
            seen.add(x to y)
            if (x != null && y != null) x + y else null
        }
        a.tryEmit(point(200.0)); b.tryEmit(point(100.0)); runCurrent()
        assertEquals(300.0, emitter.last().value(), 1e-9)

        b.tryEmit(StreamState.Searching); runCurrent()
        assertEquals(StreamState.Searching, emitter.last())
        assertEquals(200.0 to null, seen.last())
    }

    // --- three sources ---

    @Test
    fun `triple - emits once all three resolve`() = runTest(UnconfinedTestDispatcher()) {
        val a = newFlow(); val b = newFlow(); val c = newFlow()
        val emitter = RecordingEmitter()
        backgroundScope.collectStreamMetric3(a, b, c, "id", "f", emitter) { _, x, y, z ->
            if (x != null && y != null && z != null) x + y + z else null
        }
        a.tryEmit(point(1.0)); b.tryEmit(point(2.0)); runCurrent()
        assertTrue(emitter.states.all { it is StreamState.Searching })

        c.tryEmit(point(3.0)); runCurrent()
        assertEquals(6.0, emitter.last().value(), 1e-9)
    }
}
