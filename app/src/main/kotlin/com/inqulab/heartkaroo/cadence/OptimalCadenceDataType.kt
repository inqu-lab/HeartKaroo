package com.inqulab.heartkaroo.cadence

import com.inqulab.heartkaroo.karoo.collectStreamMetric3
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
 * Live Karoo data field: best-efficiency cadence learned from the
 * current ride. Reads — until at least two cadence bins have enough
 * samples to compare.
 */
class OptimalCadenceDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val hrFlow: Flow<StreamState>,
    private val cadenceFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "optimal_cadence"
        const val FIELD = "optimal_cadence"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = OptimalCadenceCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric3(powerFlow, hrFlow, cadenceFlow, dataTypeId, FIELD, emitter) { _, p, h, c ->
            if (p != null && h != null && c != null) calc.add(p, h, c)
            calc.optimalCadence()?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
