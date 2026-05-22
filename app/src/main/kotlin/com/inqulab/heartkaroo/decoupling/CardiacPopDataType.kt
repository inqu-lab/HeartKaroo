package com.inqulab.heartkaroo.decoupling

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Latching field: the minute mark at which Pw:Hr decoupling first crossed (and
 * stayed above) the threshold. Computed continuously in RidePowerEngine so the
 * latch survives page switches.
 */
class CardiacPopDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "cardiac_pop_minute"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.cardiacPop, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
