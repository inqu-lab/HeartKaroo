package com.inqulab.heartkaroo.hrv

import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig

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

    // α1 is a ~0.5–1.5 ratio. Without a format hint Karoo renders a custom
    // numeric field as a whole number (0, 1, 2). Borrow Intensity Factor's
    // formatting — dimensionless, two decimals, no unit conversion — so α1 reads
    // as e.g. "0.75".
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.INTENSITY_FACTOR))
        emitter.setCancellable {}
    }
}
