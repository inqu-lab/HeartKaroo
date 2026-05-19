package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HRVDataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "hrv_rmssd"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.NotAvailable)
        val job = CoroutineScope(Dispatchers.IO).launch {
            bleManager.rmssdFlow.collect { rmssd ->
                if (rmssd > 0f) {
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to rmssd.toDouble()),
                            )
                        )
                    )
                } else {
                    emitter.onNext(StreamState.NotAvailable)
                }
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
