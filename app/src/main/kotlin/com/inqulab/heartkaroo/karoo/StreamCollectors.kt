package com.inqulab.heartkaroo.karoo

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** The first numeric value of a Karoo stream state, or null if it isn't
 *  currently streaming a value. */
internal fun StreamState.singleValue(): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()

private fun Emitter<StreamState>.emitMetric(dataTypeId: String, field: String, value: Double?) {
    onNext(
        if (value == null) StreamState.Searching
        else StreamState.Streaming(DataPoint(dataTypeId, mapOf(field to value))),
    )
}

/**
 * Collects a single Karoo stream, feeds each numeric sample to [compute],
 * and forwards the result to Karoo (null -> Searching, else Streaming).
 * Non-streaming upstream states (Searching/Idle) are skipped. Shared by the
 * single-input data fields and kept free of the Android stack so the
 * mapping is unit-testable; [now] is injectable for deterministic tests.
 */
internal fun CoroutineScope.collectStreamMetric(
    source: Flow<StreamState>,
    dataTypeId: String,
    field: String,
    emitter: Emitter<StreamState>,
    now: () -> Long = System::currentTimeMillis,
    compute: (timeMs: Long, value: Double) -> Double?,
): Job = launch {
    source.collect { s ->
        val v = s.singleValue() ?: return@collect
        emitter.emitMetric(dataTypeId, field, compute(now(), v))
    }
}

/**
 * Collects two combined Karoo streams. Each value is null when its stream
 * has no current sample, so [compute] decides what to do (e.g. add a paired
 * sample vs. hold the last result).
 */
internal fun CoroutineScope.collectStreamMetric2(
    sourceA: Flow<StreamState>,
    sourceB: Flow<StreamState>,
    dataTypeId: String,
    field: String,
    emitter: Emitter<StreamState>,
    now: () -> Long = System::currentTimeMillis,
    compute: (timeMs: Long, a: Double?, b: Double?) -> Double?,
): Job = launch {
    combine(sourceA, sourceB) { a, b -> a.singleValue() to b.singleValue() }
        .collect { (a, b) -> emitter.emitMetric(dataTypeId, field, compute(now(), a, b)) }
}

/** Three-stream variant of [collectStreamMetric2]. */
internal fun CoroutineScope.collectStreamMetric3(
    sourceA: Flow<StreamState>,
    sourceB: Flow<StreamState>,
    sourceC: Flow<StreamState>,
    dataTypeId: String,
    field: String,
    emitter: Emitter<StreamState>,
    now: () -> Long = System::currentTimeMillis,
    compute: (timeMs: Long, a: Double?, b: Double?, c: Double?) -> Double?,
): Job = launch {
    combine(sourceA, sourceB, sourceC) { a, b, c ->
        Triple(a.singleValue(), b.singleValue(), c.singleValue())
    }.collect { (a, b, c) -> emitter.emitMetric(dataTypeId, field, compute(now(), a, b, c)) }
}
