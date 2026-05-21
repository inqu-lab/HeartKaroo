package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Live Training Stress Score (Coggan) — TSS = (s × NP × IF) / (FTP × 3600) × 100,
 * which simplifies to TSS = duration_hours × IF² × 100.
 *
 * Updates continuously through the ride. Treats NP over the whole
 * elapsed window and FTP from RiderSettings.
 */
class TssDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "tss"
        const val FIELD = "tss"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        // Window the whole ride (4 h) — TSS is cumulative.
        val np = NormalizedPowerCalculator(windowMs = 4 * 60 * 60 * 1000L)
        val settings = RiderSettings(parent.applicationContext)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job = scope.collectPowerMetric(
            parent.karooSystem.streamDataFlow(DataType.Type.POWER),
            dataTypeId,
            FIELD,
            emitter,
        ) { t, p ->
            np.add(t, p)
            PowerMetrics.trainingStressScore(np.normalizedPower(), settings.ftpW, np.elapsedSec())
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
