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

class KilojoulesDataType(
    extensionId: String,
    private val powerFlow: Flow<StreamState>,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "kilojoules"
        const val FIELD = "kilojoules"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val calc = KilojoulesCalculator()
        val scope = CoroutineScope(dispatcher + SupervisorJob())
        val job = scope.collectStreamMetric(powerFlow, dataTypeId, FIELD, emitter) { t, p ->
            calc.add(t, p).toDouble()
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }
}
