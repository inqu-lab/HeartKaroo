package com.inqulab.heartkaroo.hrv

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Default time a field keeps showing its last value through a data gap before
 *  it goes Idle. Covers brief glitches (an ectopic beat, a noisy RR sample)
 *  without flickering, while still surfacing a real, sustained loss. */
private const val HOLD_MS = 8_000L

private fun streaming(dataTypeId: String, value: Double): StreamState.Streaming =
    StreamState.Streaming(
        DataPoint(dataTypeId = dataTypeId, values = mapOf(DataType.Field.SINGLE to value)),
    )

/**
 * Collects a Float? source and forwards it to Karoo as a StreamState, using the
 * strap's [connected] state to pick the right "no value" state:
 *  - not connected            -> Searching (no sensor to read from)
 *  - connected, has value     -> Streaming
 *  - connected, value missing  -> if we had a value, hold it for [holdMs] (so a
 *    brief glitch doesn't flicker) then Searching; if we never had one (warming
 *    up — DFA α1 needs ~2 min of beats), Searching right away. Never a bare 0:
 *    Karoo renders Idle as "0", which reads like a real measurement.
 *
 * Returns a cancel handle for the underlying coroutine.
 */
internal fun streamFloatWithHold(
    source: Flow<Float?>,
    connected: Flow<Boolean>,
    dataTypeId: String,
    emitter: Emitter<StreamState>,
    holdMs: Long = HOLD_MS,
    // Single-threaded by default so the collector and the hold timer can share
    // lastValue/holdJob without a data race; overridable for tests.
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default.limitedParallelism(1)),
): () -> Unit {
    val job = scope.launch {
        var lastValue: Double? = null
        var holdJob: Job? = null
        combine(source, connected) { value, isConnected -> value to isConnected }
            .collect { (value, isConnected) ->
                when {
                    value != null -> {
                        holdJob?.cancel()
                        holdJob = null
                        lastValue = value.toDouble()
                        emitter.onNext(streaming(dataTypeId, value.toDouble()))
                    }
                    !isConnected -> {
                        holdJob?.cancel()
                        holdJob = null
                        lastValue = null
                        emitter.onNext(StreamState.Searching)
                    }
                    lastValue != null && holdJob == null -> {
                        // Connected, brief gap: keep the last value, then Searching.
                        // (The source is a StateFlow, so a sustained null won't
                        // re-emit; the timer is what flips us over.)
                        emitter.onNext(streaming(dataTypeId, lastValue!!))
                        holdJob = launch {
                            delay(holdMs)
                            lastValue = null
                            holdJob = null
                            emitter.onNext(StreamState.Searching)
                        }
                    }
                    lastValue == null && holdJob == null -> {
                        // Connected but no value yet (warming up) or hold expired.
                        emitter.onNext(StreamState.Searching)
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
    private val connected: Flow<Boolean>,
    // When set, render the value with this built-in type's precision (e.g. a
    // dimensionless ratio that Karoo would otherwise show as a whole number).
    private val formatDataTypeId: String? = null,
) : DataTypeImpl(extensionId, typeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatWithHold(source, connected, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (formatDataTypeId == null) return
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = formatDataTypeId))
        emitter.setCancellable {}
    }
}
