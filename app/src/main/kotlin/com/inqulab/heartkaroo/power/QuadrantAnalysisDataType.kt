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
import kotlin.math.roundToInt

class QuadrantAnalysisDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "quadrant"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.quadrant, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = quadrantZone(2f)
            emitter.updateView(buildZoneView(context, format(2f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(context, parent.ridePowerEngine.quadrant, emitter, "Quad", ::format, ::quadrantZone)
    }

    private fun format(quadrant: Float): String = "Q${quadrant.roundToInt()}"
}
