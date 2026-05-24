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

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = decouplingZone(3.2f)
            emitter.updateView(buildZoneView(context, format(3.2f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(
            context, parent.ridePowerEngine.paHrDecoupling, emitter, "Pa:Hr", ::format, ::decouplingZone,
        )
    }

    private fun format(percent: Float): String = String.format(Locale.US, "%.1f%%", percent)
}
