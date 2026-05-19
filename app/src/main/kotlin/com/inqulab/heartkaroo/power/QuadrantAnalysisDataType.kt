package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class QuadrantAnalysisDataType(
    private val extension: HeartKarooExtension,
) : DataTypeImpl(extension.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "quadrant"
        const val FIELD = "quadrant"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val settings = RiderSettings(extension.applicationContext)
        val calc = QuadrantAnalysisCalculator(ftpW = settings.ftpW.toDouble())
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            combine(
                extension.karooSystem.streamDataFlow(DataType.Type.POWER),
                extension.karooSystem.streamDataFlow(DataType.Type.CADENCE),
            ) { ps, cs ->
                Pair(
                    (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (cs as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                )
            }.collect { (p, c) ->
                if (p != null && c != null) {
                    calc.add(System.currentTimeMillis(), p, c)
                }
                val q = calc.dominantQuadrant()
                emitter.onNext(
                    if (q == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to q.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
