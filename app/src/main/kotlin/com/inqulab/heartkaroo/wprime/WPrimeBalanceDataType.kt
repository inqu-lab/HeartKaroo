package com.inqulab.heartkaroo.wprime

import android.content.Context
import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.buildZoneView
import com.inqulab.heartkaroo.karoo.startZoneView
import com.inqulab.heartkaroo.karoo.streamFloatState
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import java.util.Locale

/**
 * Skiba W′ balance (anaerobic capacity remaining, J). The running balance is
 * integrated over the whole ride in RidePowerEngine, so it no longer snaps back
 * to full W′₀ whenever the field's page is hidden. CP / W′₀ are read from
 * RiderSettings and refreshed each ride.
 */
class WPrimeBalanceDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "w_prime_balance"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatState(parent.ridePowerEngine.wPrimeBalance, dataTypeId, emitter)
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val maxJ = RiderSettings(context).wPrimeJ.toFloat()
        if (config.preview) {
            val z = wPrimeZone(0.6f * maxJ, maxJ)
            emitter.updateView(buildZoneView(context, format(0.6f * maxJ), z.label, z.color))
            emitter.setCancellable {}
            return
        }
        startZoneView(
            context, parent.ridePowerEngine.wPrimeBalance, emitter, "W′",
            format = ::format, classify = { wPrimeZone(it, maxJ) },
        )
    }

    private fun format(balanceJ: Float): String = String.format(Locale.US, "%.0f", balanceJ)
}
