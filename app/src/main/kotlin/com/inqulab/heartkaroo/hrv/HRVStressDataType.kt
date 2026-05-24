package com.inqulab.heartkaroo.hrv

import android.content.Context
import com.inqulab.heartkaroo.karoo.buildZoneView
import com.inqulab.heartkaroo.karoo.startZoneView
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import java.util.Locale

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

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = hrvStressZone(45f)
            emitter.updateView(buildZoneView(context, format(45f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(context, bleManager.stressFlow, emitter, "Stress", ::format, ::hrvStressZone)
    }

    private fun format(stressPercent: Float): String = String.format(Locale.US, "%.0f%%", stressPercent)
}
