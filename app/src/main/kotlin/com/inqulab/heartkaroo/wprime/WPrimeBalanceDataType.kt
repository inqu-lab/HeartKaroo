package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.HeartKarooExtension
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
 * in joules).
 *
 * CP and W′₀ default to broadly typical values (250 W, 20 000 J); a
 * settings screen to personalise these is intentionally deferred.
 */
class WPrimeBalanceDataType(
    private val extension: HeartKarooExtension,
) : DataTypeImpl(extension.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "w_prime_balance"
        const val FIELD = "w_prime_balance"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = WPrimeBalanceCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            extension.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
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
