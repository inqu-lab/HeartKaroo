package com.inqulab.heartkaroo.power

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
        val job: Job = scope.launch {
            parent.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                np.add(System.currentTimeMillis(), p)
                val ftp = settings.ftpW.coerceAtLeast(1)
                val n = np.normalizedPower()
                val tss = if (n == null) null
                else {
                    val hours = np.elapsedSec() / 3600.0
                    val iF = n / ftp
                    (hours * iF * iF * 100.0).toFloat()
                }
                emitter.onNext(
                    if (tss == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to tss.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
