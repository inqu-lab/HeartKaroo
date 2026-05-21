package com.inqulab.heartkaroo.efficiency

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Cardiac cost — average HR divided by average power (bpm/W), the inverse of
 * Efficiency Factor. Computed continuously in RidePowerEngine.
 */
class CardiacCostDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "cardiac_cost"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.cardiacCost, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
