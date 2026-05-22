package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Mean Maximal Power for a fixed duration over the current ride. The
 * calculators live in RidePowerEngine (keyed by typeId) so the rolling best
 * survives page switches; this field just streams the engine's value.
 */
class MmpDataType(
    private val parent: HeartKarooExtension,
    private val mmpTypeId: String,
) : DataTypeImpl(parent.extension, mmpTypeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.mmpFlow(mmpTypeId), dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
