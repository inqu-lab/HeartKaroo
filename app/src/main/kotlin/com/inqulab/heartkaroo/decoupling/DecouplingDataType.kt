package com.inqulab.heartkaroo.decoupling

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
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job = scope.collectDecoupling(
            parent.karooSystem.streamDataFlow(DataType.Type.POWER),
            parent.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            DecouplingCalculator(),
            dataTypeId,
            FIELD,
            emitter,
        )
        emitter.setCancellable {
            job.cancel()
            scope.cancel()
        }
    }
}
