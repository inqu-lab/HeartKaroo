package com.inqulab.heartkaroo.testutil

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState

/** Captures the StreamStates a DataType emits, for assertions in tests. */
class RecordingEmitter : Emitter<StreamState> {
    val states = mutableListOf<StreamState>()
    override fun onNext(t: StreamState) { states.add(t) }
    override fun onError(t: Throwable) {}
    override fun onComplete() {}
    override fun setCancellable(cancellable: () -> Unit) {}
    override fun cancel() {}
    fun last(): StreamState = states.last()
}

fun streamPoint(value: Double): StreamState =
    StreamState.Streaming(DataPoint("x", mapOf("v" to value)))

fun StreamState.firstValue(): Double =
    (this as StreamState.Streaming).dataPoint.values.values.first()
