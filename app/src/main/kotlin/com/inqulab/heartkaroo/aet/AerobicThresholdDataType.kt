package com.inqulab.heartkaroo.aet

import com.inqulab.heartkaroo.HeartKarooExtension
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Karoo data field: live within-ride aerobic-threshold estimate (watts)
 * derived from DFA α1 crossing 0.75.
 *
 * Subscribes to the Karoo power stream and the BLE-derived α1 flow,
 * pairs them inside an `AerobicThresholdCalibrator`, and emits the
 * current estimate. Reads `--` until enough varied data has been seen.
 */
class AerobicThresholdDataType(
    private val extension: HeartKarooExtension,
) : DataTypeImpl(extension.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "aet_estimate"
        const val FIELD = "aet_estimate"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = AerobicThresholdCalibrator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val powerJob: Job = scope.launch {
            extension.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                calc.addPower(System.currentTimeMillis(), p)
            }
        }
        val alphaJob: Job = scope.launch {
            extension.bleManager.dfaAlpha1Flow.filterNotNull().collect { a ->
                calc.addAlpha(a)
                val est = calc.currentEstimate()
                val state = if (est == null) StreamState.NotAvailable
                else StreamState.Streaming(
                    DataPoint(dataTypeId, mapOf(FIELD to est.toDouble())),
                )
                emitter.onNext(state)
            }
        }
        emitter.setCancellable {
            powerJob.cancel()
            alphaJob.cancel()
            scope.cancel()
        }
    }
}
