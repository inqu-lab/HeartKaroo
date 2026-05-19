package com.inqulab.heartkaroo.efficiency

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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class CardiacCostDataType(
    private val extension: HeartKarooExtension,
) : DataTypeImpl(extension.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "cardiac_cost"
        const val FIELD = "cardiac_cost"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = CardiacCostCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            combine(
                extension.karooSystem.streamDataFlow(DataType.Type.POWER),
                extension.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            ) { ps, hs ->
                Pair(
                    (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (hs as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                )
            }.collect { (p, h) ->
                val now = System.currentTimeMillis()
                val cc = if (p != null && h != null) calc.add(now, p, h) else calc.current()
                emitter.onNext(
                    if (cc == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to cc.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
