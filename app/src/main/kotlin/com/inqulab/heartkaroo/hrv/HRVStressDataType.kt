package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Custom data field exposed to Karoo as "HRV Stress %".
 *
 * Reads the stress flow maintained by PolarBleManager (EMA baseline of RMSSD,
 * compared against the current value) and forwards each update to Karoo.
 */
class HRVStressDataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "hrv_stress"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatWithHold(
            bleManager.stressFlow, bleManager.connectedFlow, dataTypeId, emitter,
        )
        emitter.setCancellable { cancel() }
    }
}
