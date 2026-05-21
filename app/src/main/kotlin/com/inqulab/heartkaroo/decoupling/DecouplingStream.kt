package com.inqulab.heartkaroo.decoupling

import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Collects a numerator stream (power or speed) paired with heart rate,
 * feeds samples into [calc], and forwards the rolling decoupling % to
 * Karoo. Shared by the Pw:Hr and Pa:Hr fields and kept free of the Android
 * stack so the mapping is unit-testable. A null result (warmup, no data)
 * becomes [StreamState.Searching]. [now] is injectable for deterministic
 * tests.
 */
internal fun CoroutineScope.collectDecoupling(
    numeratorFlow: Flow<StreamState>,
    hrFlow: Flow<StreamState>,
    calc: DecouplingCalculator,
    dataTypeId: String,
    field: String,
    emitter: Emitter<StreamState>,
    now: () -> Long = System::currentTimeMillis,
): Job = launch {
    combine(numeratorFlow, hrFlow) { numState, hrState ->
        Pair(
            (numState as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
            (hrState as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
        )
    }.collect { (num, hr) ->
        val pct = if (num != null && hr != null) calc.add(now(), num, hr) else calc.current()
        emitter.onNext(
            if (pct == null) StreamState.Searching
            else StreamState.Streaming(DataPoint(dataTypeId, mapOf(field to pct))),
        )
    }
}
