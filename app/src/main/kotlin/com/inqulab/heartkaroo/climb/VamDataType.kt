package com.inqulab.heartkaroo.climb

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

class VamDataType(
    private val extension: HeartKarooExtension,
) : DataTypeImpl(extension.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "vam"
        const val FIELD = "vam"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = VamCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            extension.karooSystem.streamDataFlow(DataType.Type.ELEVATION).collect { es ->
                val e = (es as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                val v = calc.add(System.currentTimeMillis(), e)
                emitter.onNext(
                    if (v == null) StreamState.NotAvailable
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to v.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
