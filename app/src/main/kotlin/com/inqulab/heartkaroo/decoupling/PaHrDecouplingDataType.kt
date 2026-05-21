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
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job = scope.collectDecoupling(
            parent.karooSystem.streamDataFlow(DataType.Type.SPEED),
            parent.karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            DecouplingCalculator(),
            dataTypeId,
            FIELD,
            emitter,
        )
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
