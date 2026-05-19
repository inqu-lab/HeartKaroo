package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
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

class CoastingDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "coasting_pct"
        const val FIELD = "coasting_pct"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = CoastingCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            parent.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                val pct = calc.add(System.currentTimeMillis(), p)
                emitter.onNext(
                    if (pct == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to pct.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
