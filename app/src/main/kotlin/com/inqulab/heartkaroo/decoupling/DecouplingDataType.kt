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
 * Custom data field exposed to Karoo as "Pw:Hr Decoupling".
 *
 * Subscribes to live power and heart-rate streams, feeds them into a
 * rolling DecouplingCalculator, and emits the current decoupling % once
 * per second.
 */
class DecouplingDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "decoupling"
        const val FIELD = "decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = DecouplingCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        val job: Job = scope.launch {
            combine(
                parent.karooSystem.streamDataFlow(DataType.Type.POWER),
                parent.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            ) { powerState, hrState ->
                val p = (powerState as? StreamState.Streaming)?.dataPoint?.singleValue
                val h = (hrState as? StreamState.Streaming)?.dataPoint?.singleValue
                Pair(p, h)
            }.collect { (p, h) ->
                val now = System.currentTimeMillis()
                val pct = if (p != null && h != null) calc.add(now, p, h) else calc.current()
                val state = if (pct == null) {
                    StreamState.Searching
                } else {
                    StreamState.Streaming(
                        DataPoint(
                            dataTypeId = dataTypeId,
                            values = mapOf(FIELD to pct),
                        ),
                    )
                }
                emitter.onNext(state)
            }
        }

        emitter.setCancellable {
            job.cancel()
            scope.cancel()
        }
    }
}

private val DataPoint.singleValue: Double?
    get() = values.values.firstOrNull()
