package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.karoo.collectStreamMetric
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

/**
 * Karoo data field for Skiba W′ balance (anaerobic capacity remaining,
 * in joules). CP and W′₀ are read each time the stream starts, so
 * adjusting them in the Settings screen takes effect on the next field
 * subscription.
 */
class WPrimeBalanceDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val criticalPowerW: () -> Int,
    private val wPrimeJ: () -> Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "w_prime_balance"
        const val FIELD = "w_prime_balance"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = WPrimeBalanceCalculator(
            criticalPowerW = criticalPowerW().toDouble(),
            wPrimeJ = wPrimeJ().toDouble(),
        )
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric(powerFlow, dataTypeId, FIELD, emitter) { t, p ->
            calc.add(t, p).toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
