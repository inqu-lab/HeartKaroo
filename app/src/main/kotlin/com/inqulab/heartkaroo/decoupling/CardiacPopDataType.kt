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
 * Latching data field that records the minute mark at which Pw:Hr
 * decoupling first crossed (and stayed above) the configured threshold.
 *
 * Emits StreamState.Searching before the pop, then a constant minutes
 * value for the rest of the ride.
 */
class CardiacPopDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val hrFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "cardiac_pop_minute"
        const val FIELD = "cardiac_pop_minute"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val decouplingCalc = DecouplingCalculator()
        val popDetector = CardiacPopDetector()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric2(powerFlow, hrFlow, dataTypeId, FIELD, emitter) { t, p, h ->
            val pct = if (p != null && h != null) decouplingCalc.add(t, p, h) else decouplingCalc.current()
            popDetector.add(t, pct)
            popDetector.getPopMinutes()?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
