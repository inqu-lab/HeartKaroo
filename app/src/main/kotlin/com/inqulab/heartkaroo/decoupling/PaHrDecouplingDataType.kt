package com.inqulab.heartkaroo.decoupling

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
 * Pace-based aerobic decoupling — Friel's method but using speed (m/s)
 * instead of power. Lets riders without a power meter (or runners
 * pairing the Karoo) get a decoupling signal.
 */
class PaHrDecouplingDataType(
    extensionId: String,
    private val speedFlow: Flow<StreamState>,
    private val hrFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "pace_hr_decoupling"
        const val FIELD = "pace_hr_decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = DecouplingCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric2(speedFlow, hrFlow, dataTypeId, FIELD, emitter) { t, s, h ->
            if (s != null && h != null) calc.add(t, s, h) else calc.current()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
