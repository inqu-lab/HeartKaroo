package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Karoo data field for Skiba W′ balance (anaerobic capacity remaining,
 * in joules). CP and W′₀ are read from RiderSettings each time the
 * stream starts, so adjusting them in the Settings screen takes effect
 * on the next field subscription.
 */
class WPrimeBalanceDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "w_prime_balance"
        const val FIELD = "w_prime_balance"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val settings = RiderSettings(extension.applicationContext)
        val calc = WPrimeBalanceCalculator(
            criticalPowerW = settings.criticalPowerW.toDouble(),
            wPrimeJ = settings.wPrimeJ.toDouble(),
        )
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            parent.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull() ?: return@collect
                val balance = calc.add(System.currentTimeMillis(), p)
                emitter.onNext(
                    StreamState.Streaming(
                        DataPoint(dataTypeId, mapOf(FIELD to balance.toDouble())),
                    ),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
