package com.inqulab.heartkaroo.hrv

import android.content.Context
import com.inqulab.heartkaroo.karoo.Zone
import com.inqulab.heartkaroo.karoo.buildZoneView
import com.inqulab.heartkaroo.karoo.startZoneView
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import java.util.Locale

/**
 * Custom data field exposed to Karoo as "DFA α1".
 *
 * Reads the α1 flow maintained by PolarBleManager, recomputed each time
 * a new RR interval arrives from the BLE Heart Rate Measurement notification.
 *
 * Rendered as a graphical field whose background colour and label show which
 * side of LT1 / LT2 the rider is on right now (see [DfaZone]).
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

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = zoneOf(0.62f)
            emitter.updateView(buildZoneView(context, format(0.62f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(context, bleManager.dfaAlpha1Flow, emitter, "DFA α1", ::format, ::zoneOf)
    }

    private fun format(alpha: Float): String = String.format(Locale.US, "%.2f", alpha)

    private fun zoneOf(alpha: Float): Zone =
        classifyDfaZone(alpha.toDouble()).let { Zone(it.label, it.color) }
}
