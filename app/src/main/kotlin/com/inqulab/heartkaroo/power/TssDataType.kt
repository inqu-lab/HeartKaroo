package com.inqulab.heartkaroo.power

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
 * Live Training Stress Score (Coggan) — TSS = (s × NP × IF) / (FTP × 3600) × 100,
 * which simplifies to TSS = duration_hours × IF² × 100.
 *
 * Updates continuously through the ride. Treats NP over the whole
 * elapsed window and FTP from RiderSettings.
 */
class TssDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val ftpW: () -> Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "tss"
        const val FIELD = "tss"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        // Window the whole ride (4 h) — TSS is cumulative.
        val np = NormalizedPowerCalculator(windowMs = 4 * 60 * 60 * 1000L)
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric(powerFlow, dataTypeId, FIELD, emitter) { t, p ->
            np.add(t, p)
            PowerMetrics.trainingStressScore(np.normalizedPower(), ftpW(), np.elapsedSec())?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
