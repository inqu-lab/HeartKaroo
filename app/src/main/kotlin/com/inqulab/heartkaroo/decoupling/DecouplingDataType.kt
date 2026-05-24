package com.inqulab.heartkaroo.decoupling

import android.content.Context
import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.buildZoneView
import com.inqulab.heartkaroo.karoo.startZoneView
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import java.util.Locale

/**
 * "Pw:Hr Decoupling" — Friel-method aerobic decoupling over the ride. Computed
 * continuously in RidePowerEngine (it compares the ride's first half against its
 * second, so it must accumulate across page switches).
 */
class DecouplingDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "decoupling"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.decoupling, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = decouplingZone(3.2f)
            emitter.updateView(buildZoneView(context, format(3.2f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(
            context, parent.ridePowerEngine.decoupling, emitter, "Pw:Hr", ::format, ::decouplingZone,
        )
    }

    private fun format(percent: Float): String = String.format(Locale.US, "%.1f%%", percent)
}
