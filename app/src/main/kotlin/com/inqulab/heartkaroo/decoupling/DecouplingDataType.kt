package com.inqulab.heartkaroo.decoupling

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * "Pw:Hr Decoupling" — Friel-method aerobic decoupling over the ride. Computed
 * continuously in RidePowerEngine (it compares the ride's first half against its
 * second, so it must accumulate across page switches).
 */
class DecouplingDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.decoupling, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
