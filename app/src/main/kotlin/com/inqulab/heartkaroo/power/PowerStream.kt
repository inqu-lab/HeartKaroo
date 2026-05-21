package com.inqulab.heartkaroo.power

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Collects a power [StreamState] flow, feeds each numeric power sample to
 * [compute], and forwards the result to Karoo: a null result becomes
 * [StreamState.Searching], otherwise a single-value [StreamState.Streaming]
 * for [field]. Shared by the VI / IF / TSS power fields and kept free of
 * the Android stack so the mapping is unit-testable. [now] is injectable so
 * tests can supply deterministic timestamps.
 */
internal fun CoroutineScope.collectPowerMetric(
    powerFlow: Flow<StreamState>,
    dataTypeId: String,
    field: String,
    emitter: Emitter<StreamState>,
    now: () -> Long = System::currentTimeMillis,
    compute: (timeMs: Long, power: Double) -> Float?,
): Job = launch {
    powerFlow.collect { ps ->
        val power = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
            ?: return@collect
        val value = compute(now(), power)
        emitter.onNext(
            if (value == null) StreamState.Searching
            else StreamState.Streaming(DataPoint(dataTypeId, mapOf(field to value.toDouble()))),
        )
    }
}
