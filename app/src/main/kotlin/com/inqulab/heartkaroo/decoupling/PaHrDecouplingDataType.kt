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
 * Pace-based aerobic decoupling — Friel's method but using speed (m/s)
 * instead of power. Lets riders without a power meter (or runners
 * pairing the Karoo) get a decoupling signal.
 */
class PaHrDecouplingDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "pace_hr_decoupling"
        const val FIELD = "pace_hr_decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = DecouplingCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            combine(
                parent.karooSystem.streamDataFlow(DataType.Type.SPEED),
                parent.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            ) { s, h ->
                Pair(
                    (s as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (h as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                )
            }.collect { (speed, hr) ->
                val now = System.currentTimeMillis()
                val pct = if (speed != null && hr != null) calc.add(now, speed, hr) else calc.current()
                val state = if (pct == null) StreamState.Searching
                else StreamState.Streaming(
                    DataPoint(dataTypeId, mapOf(FIELD to pct)),
                )
                emitter.onNext(state)
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
