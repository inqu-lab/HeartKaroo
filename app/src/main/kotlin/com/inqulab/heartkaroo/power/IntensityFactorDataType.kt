package com.inqulab.heartkaroo.power

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

class IntensityFactorDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "intensity_factor"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.intensityFactor, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = intensityFactorZone(0.92f)
            emitter.updateView(buildZoneView(context, format(0.92f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(
            context, parent.ridePowerEngine.intensityFactor, emitter, "IF", ::format, ::intensityFactorZone,
        )
    }

    private fun format(intensityFactor: Float): String = String.format(Locale.US, "%.2f", intensityFactor)
}
