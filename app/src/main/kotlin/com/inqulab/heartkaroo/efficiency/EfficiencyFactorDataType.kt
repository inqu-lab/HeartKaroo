package com.inqulab.heartkaroo.efficiency

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
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

    // EF (NP/HR) is a ~1.0–3.0 ratio; without a format hint Karoo renders it as a
    // whole number. Borrow Intensity Factor's dimensionless two-decimal format.
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.INTENSITY_FACTOR))
        emitter.setCancellable {}
    }
}

private fun StreamState.singleValue(): Double? =
    (this as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
