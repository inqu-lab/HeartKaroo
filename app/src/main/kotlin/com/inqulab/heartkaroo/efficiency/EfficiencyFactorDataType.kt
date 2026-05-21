package com.inqulab.heartkaroo.efficiency

import com.inqulab.heartkaroo.karoo.collectStreamMetric2
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
 * Karoo data field for Coggan Efficiency Factor (NP / avg HR) over the
 * last 30 minutes of riding.
 */
class EfficiencyFactorDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val hrFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "efficiency_factor"
        const val FIELD = "efficiency_factor"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = EfficiencyFactorCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric2(powerFlow, hrFlow, dataTypeId, FIELD, emitter) { t, p, h ->
            (if (p != null && h != null) calc.add(t, p, h) else calc.current())?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
