package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Default time a field keeps showing its last value through a data gap before
 *  falling back to "Searching". Covers brief glitches (an ectopic beat, a noisy
 *  RR sample) without flickering, while still surfacing a real, sustained loss. */
private const val HOLD_MS = 8_000L

private fun streaming(dataTypeId: String, value: Double): StreamState.Streaming =
    StreamState.Streaming(
        DataPoint(dataTypeId = dataTypeId, values = mapOf(DataType.Field.SINGLE to value)),
    )

/**
 * Collects a Float? source and forwards it to Karoo as a StreamState, holding
 * the last value through brief nulls so transient data glitches don't flash the
 * field to "Searching". After [holdMs] of continuous no-data it shows Searching.
 * Returns a cancel handle for the underlying coroutine.
 */
internal fun streamFloatWithHold(
    source: Flow<Float?>,
    dataTypeId: String,
    emitter: Emitter<StreamState>,
    holdMs: Long = HOLD_MS,
): () -> Unit {
    emitter.onNext(StreamState.Searching)
    // Single-threaded so the collector and the hold timer can share lastValue/
    // holdJob without a data race.
    val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    val job = scope.launch {
        var lastValue: Double? = null
        var holdJob: Job? = null
        source.collect { value ->
            if (value != null) {
                holdJob?.cancel()
                holdJob = null
                lastValue = value.toDouble()
                emitter.onNext(streaming(dataTypeId, value.toDouble()))
            } else {
                val held = lastValue
                when {
                    held == null -> emitter.onNext(StreamState.Searching)
                    holdJob == null -> {
                        // Keep showing the last value, then give up after holdMs.
                        // (The source is a StateFlow, so a sustained null won't
                        // re-emit; the timer is what flips us to Searching.)
                        emitter.onNext(streaming(dataTypeId, held))
                        holdJob = launch {
                            delay(holdMs)
                            lastValue = null
                            holdJob = null
                            emitter.onNext(StreamState.Searching)
                        }
                    }
                }
            }
        }
    }
    return { job.cancel() }
}

/**
 * Thin DataType implementation that forwards each value from a Float? flow to
 * Karoo. Used for all HRV-derived single-value fields (RMSSD, stress, DFA α1,
 * SDNN, pNN50, SD1/SD2/ratio, respiratory rate, ectopic rate).
 */
class HrvFlowDataType(
    extensionId: String,
    typeId: String,
    private val source: Flow<Float?>,
) : DataTypeImpl(extensionId, typeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatWithHold(source, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
