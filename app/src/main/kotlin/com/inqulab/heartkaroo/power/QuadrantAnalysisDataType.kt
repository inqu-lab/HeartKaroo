package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.karoo.collectStreamMetric2
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow

class QuadrantAnalysisDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val cadenceFlow: Flow<StreamState>,
    private val ftpW: () -> Int,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "quadrant"
        const val FIELD = "quadrant"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = QuadrantAnalysisCalculator(ftpW = ftpW().toDouble())
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric2(powerFlow, cadenceFlow, dataTypeId, FIELD, emitter) { t, p, c ->
            if (p != null && c != null) calc.add(t, p, c)
            calc.dominantQuadrant()?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
