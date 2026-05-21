package com.inqulab.heartkaroo.aet

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Karoo data field: live within-ride aerobic-threshold estimate (watts) derived
 * from DFA α1 crossing 0.75. Computed continuously in RidePowerEngine (it needs
 * many minutes of varied riding, so it must accumulate across page switches).
 */
class AerobicThresholdDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "aet_estimate"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.aet, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
