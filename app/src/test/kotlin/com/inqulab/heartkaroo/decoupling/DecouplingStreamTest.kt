package com.inqulab.heartkaroo.decoupling

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DecouplingStreamTest {

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

    private fun newFlow() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)

    @Test
    fun `emits Searching while still warming up`() = runTest(UnconfinedTestDispatcher()) {
        val power = newFlow()
        val hr = newFlow()
        val emitter = RecordingEmitter()
        backgroundScope.collectDecoupling(
            power, hr, DecouplingCalculator(), "decoupling", "decoupling", emitter,
        ) { 0L }
        power.tryEmit(point(200.0))
        hr.tryEmit(point(100.0))
        runCurrent()

        assertEquals(StreamState.Searching, emitter.last())
    }

    @Test
    fun `emits Streaming once the calculator resolves`() = runTest(UnconfinedTestDispatcher()) {
        val calc = DecouplingCalculator(windowMs = 60 * 60 * 1000L, warmupMs = 0L)
        val power = newFlow()
        val hr = newFlow()
        val emitter = RecordingEmitter()
        var clock = 0L
        backgroundScope.collectDecoupling(
            power, hr, calc, "decoupling", "decoupling", emitter,
        ) { clock }
        power.tryEmit(point(200.0))
        hr.tryEmit(point(100.0))
        runCurrent()
        clock = 2_000L
        power.tryEmit(point(200.0))
        hr.tryEmit(point(100.0))
        runCurrent()

        assertTrue(emitter.last() is StreamState.Streaming)
        // Stable power:HR ratio across both halves -> 0% decoupling.
        assertEquals(0.0, (emitter.last() as StreamState.Streaming).dataPoint.values.values.first(), 1e-6)
    }
}
