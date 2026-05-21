package com.inqulab.heartkaroo.aet

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * Karoo data field: live within-ride aerobic-threshold estimate (watts)
 * derived from DFA α1 crossing 0.75.
 *
 * Subscribes to the Karoo power stream and the BLE-derived α1 flow,
 * pairs them inside an `AerobicThresholdCalibrator`, and emits the
 * current estimate. Reads `--` until enough varied data has been seen.
 */
class AerobicThresholdDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val alphaFlow: Flow<Float?>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "aet_estimate"
        const val FIELD = "aet_estimate"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = AerobicThresholdCalibrator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectAetEstimate(powerFlow, alphaFlow, calc, dataTypeId, FIELD, emitter)
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
