package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Live Training Stress Score (Coggan) — TSS = duration_hours × IF² × 100,
 * cumulative over the ride. Computed continuously in RidePowerEngine.
 */
class TssDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "tss"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.tss, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
