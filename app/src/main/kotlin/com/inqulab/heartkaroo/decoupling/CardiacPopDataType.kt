package com.inqulab.heartkaroo.decoupling

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Latching data field that records the minute mark at which Pw:Hr
 * decoupling first crossed (and stayed above) the configured threshold.
 *
 * Reads StreamState.NotAvailable before the pop, then a constant minutes
 * value for the rest of the ride.
 */
class CardiacPopDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "cardiac_pop_minute"
        const val FIELD = "cardiac_pop_minute"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val decouplingCalc = DecouplingCalculator()
        val popDetector = CardiacPopDetector()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            combine(
                parent.karooSystem.streamDataFlow(DataType.Type.POWER),
                parent.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            ) { ps, hs ->
                Pair(
                    (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (hs as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                )
            }.collect { (p, h) ->
                val now = System.currentTimeMillis()
                val pct = if (p != null && h != null) decouplingCalc.add(now, p, h)
                else decouplingCalc.current()
                popDetector.add(now, pct)
                val mins = popDetector.getPopMinutes()
                val state = if (mins == null) StreamState.NotAvailable
                else StreamState.Streaming(
                    DataPoint(dataTypeId, mapOf(FIELD to mins.toDouble())),
                )
                emitter.onNext(state)
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
