package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.karoo.collectStreamMetric
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

class VariabilityIndexDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "variability_index"
        const val FIELD = "variability_index"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val np = NormalizedPowerCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric(powerFlow, dataTypeId, FIELD, emitter) { t, p ->
            np.add(t, p)
            PowerMetrics.variabilityIndex(np.normalizedPower(), np.averagePower())?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
