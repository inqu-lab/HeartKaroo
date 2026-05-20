package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.flow.map

class HRVDataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "hrv_rmssd"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        // rmssdFlow uses 0 to mean "no value yet"; map it to null so the shared
        // hold logic treats it like the other HRV fields.
        val source = bleManager.rmssdFlow.map { if (it > 0f) it else null }
        val cancel = streamFloatWithHold(source, bleManager.connectedFlow, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }
}
