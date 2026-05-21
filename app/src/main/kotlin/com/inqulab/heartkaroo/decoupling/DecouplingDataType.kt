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
 * Custom data field exposed to Karoo as "Pw:Hr Decoupling".
 *
 * Subscribes to live power and heart-rate streams, feeds them into a
 * rolling DecouplingCalculator, and emits the current decoupling % once
 * per second.
 */
class DecouplingDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val hrFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "decoupling"
        const val FIELD = "decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = DecouplingCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric2(powerFlow, hrFlow, dataTypeId, FIELD, emitter) { t, p, h ->
            if (p != null && h != null) calc.add(t, p, h) else calc.current()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
