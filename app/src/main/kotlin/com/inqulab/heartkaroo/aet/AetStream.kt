package com.inqulab.heartkaroo.aet

import com.inqulab.heartkaroo.karoo.singleValue
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Pairs a Karoo power stream with the BLE-derived DFA alpha-1 flow inside
 * [calc] and emits the live aerobic-threshold estimate. Power samples only
 * feed the calibrator; each non-null alpha sample triggers a recompute and
 * an emission (null estimate -> Searching). Kept free of the Android stack
 * so the wiring is unit-testable; [now] is injectable for deterministic
 * tests.
 */
internal fun CoroutineScope.collectAetEstimate(
    powerFlow: Flow<StreamState>,
    alphaFlow: Flow<Float?>,
    calc: AerobicThresholdCalibrator,
    dataTypeId: String,
    field: String,
    emitter: Emitter<StreamState>,
    now: () -> Long = System::currentTimeMillis,
): Job = launch {
    launch {
        powerFlow.collect { ps -> ps.singleValue()?.let { calc.addPower(now(), it) } }
    }
    alphaFlow.filterNotNull().collect { alpha ->
        calc.addAlpha(alpha)
        val estimate = calc.currentEstimate()
        emitter.onNext(
            if (estimate == null) StreamState.Searching
            else StreamState.Streaming(DataPoint(dataTypeId, mapOf(field to estimate.toDouble()))),
        )
    }
}
