package com.inqulab.heartkaroo.wprime

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
 * W′ balance as a 0–100 % anaerobic "fuel gauge" — the same Skiba model as the
 * W′ balance field, but normalised by W′₀ so it reads at a glance without
 * knowing your W′ in joules. The percentage is already formed in the engine,
 * so the zone classifier runs against a fixed max of 100.
 */
class WPrimePercentDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "w_prime_pct"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.wPrimePct, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        if (config.preview) {
            val z = wPrimeZone(60f, 100f)
            emitter.updateView(buildZoneView(context, format(60f), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(
            context, parent.ridePowerEngine.wPrimePct, emitter, "W′",
            format = ::format, classify = { wPrimeZone(it, 100f) },
        )
    }

    private fun format(pct: Float): String = String.format(Locale.US, "%.0f%%", pct)
}
