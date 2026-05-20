package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

/**
 * Custom data field exposed to Karoo as "DFA α1".
 *
 * Reads the α1 flow maintained by PolarBleManager, recomputed each time
 * a new RR interval arrives from the BLE Heart Rate Measurement notification.
 */
class DfaAlpha1DataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "dfa_alpha1"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatWithHold(
            bleManager.dfaAlpha1Flow, bleManager.connectedFlow, dataTypeId, emitter,
        )
        emitter.setCancellable { cancel() }
    }
}
