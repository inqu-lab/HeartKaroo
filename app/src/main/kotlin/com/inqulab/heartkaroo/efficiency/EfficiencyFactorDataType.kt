package com.inqulab.heartkaroo.efficiency

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
 * Karoo data field for Coggan Efficiency Factor (NP / avg HR) over the
 * last 30 minutes of riding.
 */
class EfficiencyFactorDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "efficiency_factor"
        const val FIELD = "efficiency_factor"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = EfficiencyFactorCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            combine(
                parent.karooSystem.streamDataFlow(DataType.Type.POWER),
                parent.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            ) { p, h -> Pair(p.singleValue(), h.singleValue()) }
                .collect { (p, h) ->
                    val now = System.currentTimeMillis()
                    val ef = if (p != null && h != null) calc.add(now, p, h) else calc.current()
                    val state = if (ef == null) StreamState.Searching
                    else StreamState.Streaming(
                        DataPoint(dataTypeId, mapOf(FIELD to ef.toDouble())),
                    )
                    emitter.onNext(state)
                }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}

private fun StreamState.singleValue(): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
