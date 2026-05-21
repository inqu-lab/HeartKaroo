package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class VariabilityIndexDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "variability_index"
        const val FIELD = "variability_index"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val np = NormalizedPowerCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job = scope.collectPowerMetric(
            parent.karooSystem.streamDataFlow(DataType.Type.POWER),
            dataTypeId,
            FIELD,
            emitter,
        ) { t, p ->
            np.add(t, p)
            PowerMetrics.variabilityIndex(np.normalizedPower(), np.averagePower())
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
