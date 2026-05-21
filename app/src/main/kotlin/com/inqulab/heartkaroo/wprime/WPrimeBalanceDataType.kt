package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamFloatState
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState

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
}
