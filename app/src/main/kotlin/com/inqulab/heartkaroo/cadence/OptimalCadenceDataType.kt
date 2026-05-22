package com.inqulab.heartkaroo.cadence

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Live Karoo data field: best-efficiency cadence learned from the current ride.
 * Computed continuously in RidePowerEngine (it needs two well-populated cadence
 * bins, so it must accumulate across page switches).
 */
class OptimalCadenceDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "optimal_cadence"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.optimalCadence, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
