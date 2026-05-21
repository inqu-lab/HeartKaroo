package com.inqulab.heartkaroo.climb

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

class VamDataType(
    extensionId: String,
    private val elevationFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "vam"
        const val FIELD = "vam"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = VamCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric(elevationFlow, dataTypeId, FIELD, emitter) { t, e ->
            calc.add(t, e)?.toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
