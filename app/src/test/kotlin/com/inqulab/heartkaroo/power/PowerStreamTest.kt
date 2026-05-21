package com.inqulab.heartkaroo.power

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PowerStreamTest {

    private class RecordingEmitter : Emitter<StreamState> {
        val states = mutableListOf<StreamState>()
        override fun onNext(t: StreamState) { states.add(t) }
        override fun onError(t: Throwable) {}
        override fun onComplete() {}
        override fun setCancellable(cancellable: () -> Unit) {}
        override fun cancel() {}
    }

    private fun powerPoint(value: Double): StreamState =
        StreamState.Streaming(DataPoint("power", mapOf("v" to value)))

    private fun StreamState.value(): Double =
        (this as StreamState.Streaming).dataPoint.values.values.first()

    @Test
    fun `null compute result maps to Searching, non-null to Streaming`() = runTest(UnconfinedTestDispatcher()) {
        val emitter = RecordingEmitter()
        backgroundScope.collectPowerMetric(
            flowOf(powerPoint(50.0), powerPoint(200.0)),
            "vi",
            "vi",
            emitter,
            now = { 1_000L },
        ) { _, p -> if (p >= 100.0) p.toFloat() else null }
        runCurrent()

        assertEquals(2, emitter.states.size)
        assertEquals(StreamState.Searching, emitter.states[0])
        assertTrue(emitter.states[1] is StreamState.Streaming)
        assertEquals(200.0, emitter.states[1].value(), 1e-6)
    }

    @Test
    fun `non-streaming input is ignored`() = runTest(UnconfinedTestDispatcher()) {
        val emitter = RecordingEmitter()
        backgroundScope.collectPowerMetric(
            flowOf(StreamState.Searching, StreamState.Idle, powerPoint(200.0)),
            "vi",
            "vi",
            emitter,
            now = { 1_000L },
        ) { _, p -> p.toFloat() }
        runCurrent()

        // Only the one Streaming power sample produces an emission.
        assertEquals(1, emitter.states.size)
        assertEquals(200.0, emitter.states[0].value(), 1e-6)
    }

    @Test
    fun `compute receives the injected timestamp`() = runTest(UnconfinedTestDispatcher()) {
        val emitter = RecordingEmitter()
        val seen = mutableListOf<Long>()
        backgroundScope.collectPowerMetric(
            flowOf(powerPoint(100.0)),
            "vi",
            "vi",
            emitter,
            now = { 4_242L },
        ) { t, p -> seen.add(t); p.toFloat() }
        runCurrent()

        assertEquals(listOf(4_242L), seen)
    }
}
