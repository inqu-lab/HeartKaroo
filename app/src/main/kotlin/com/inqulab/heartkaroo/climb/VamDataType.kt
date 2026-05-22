package com.inqulab.heartkaroo.climb

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * VAM (vertical ascent metres per hour) over a rolling 60-s window. Computed in
 * RidePowerEngine so it doesn't re-warm every time the field's page is shown.
 */
class VamDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "vam"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.vam, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
