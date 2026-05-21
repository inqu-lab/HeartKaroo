package com.inqulab.heartkaroo.aet

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
class AetStreamTest {

    private class RecordingEmitter : Emitter<StreamState> {
        val states = mutableListOf<StreamState>()
        override fun onNext(t: StreamState) { states.add(t) }
        override fun onError(t: Throwable) {}
        override fun onComplete() {}
        override fun setCancellable(cancellable: () -> Unit) {}
        override fun cancel() {}
        fun last() = states.last()
    }

    private fun powerPoint(value: Double): StreamState =
        StreamState.Streaming(DataPoint("power", mapOf("v" to value)))

    private fun StreamState.value(): Double =
        (this as StreamState.Streaming).dataPoint.values.values.first()

    private fun powerFlow() = MutableSharedFlow<StreamState>(replay = 1, extraBufferCapacity = 16)
    private fun alphaFlow() = MutableSharedFlow<Float?>(replay = 1, extraBufferCapacity = 16)

    @Test
    fun `Searching until enough pairs, then the fitted estimate streams`() = runTest(UnconfinedTestDispatcher()) {
        val power = powerFlow()
        val alpha = alphaFlow()
        val calc = AerobicThresholdCalibrator(minSamples = 2)
        val emitter = RecordingEmitter()
        var clock = 0L
        backgroundScope.collectAetEstimate(power, alpha, calc, "aet", "aet", emitter) { clock }

        power.tryEmit(powerPoint(100.0)); runCurrent()
        alpha.tryEmit(0.9f); runCurrent()
        assertEquals(StreamState.Searching, emitter.last())

        // Second pair at a higher power with lower alpha -> negative slope,
        // alpha hits 0.75 at ~150 W. (Advance past the 30 s power window so
        // the second alpha pairs with 200 W, not the average of both.)
        clock = 60_000L
        power.tryEmit(powerPoint(200.0)); runCurrent()
        alpha.tryEmit(0.6f); runCurrent()

        assertTrue(emitter.last() is StreamState.Streaming)
        assertEquals(150.0, emitter.last().value(), 1.0)
    }

    @Test
    fun `null alpha samples never trigger an emission`() = runTest(UnconfinedTestDispatcher()) {
        val power = powerFlow()
        val alpha = alphaFlow()
        val calc = AerobicThresholdCalibrator(minSamples = 2)
        val emitter = RecordingEmitter()
        backgroundScope.collectAetEstimate(power, alpha, calc, "aet", "aet", emitter) { 0L }

        power.tryEmit(powerPoint(100.0)); runCurrent()
        alpha.tryEmit(null); runCurrent()

        assertTrue(emitter.states.isEmpty())
    }
}
