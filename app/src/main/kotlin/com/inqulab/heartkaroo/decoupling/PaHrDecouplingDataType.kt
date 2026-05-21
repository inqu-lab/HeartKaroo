package com.inqulab.heartkaroo.decoupling

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Pace-based aerobic decoupling (Friel's method using speed instead of power).
 * Computed continuously in RidePowerEngine so it accumulates across page
 * switches.
 */
class PaHrDecouplingDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "pace_hr_decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.paHrDecoupling, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
