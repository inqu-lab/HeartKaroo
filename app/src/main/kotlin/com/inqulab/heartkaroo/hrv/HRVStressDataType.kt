package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Custom data field exposed to Karoo as "HRV Stress %".
 *
 * Reads the stress flow maintained by PolarBleManager (EMA baseline of RMSSD,
 * compared against the current value) and forwards each update to Karoo.
 */
class HRVStressDataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "hrv_stress"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val job = CoroutineScope(Dispatchers.IO).launch {
            bleManager.stressFlow
                .filterNotNull()
                .collect { stress ->
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to stress.toDouble()),
                            )
                        )
                    )
                }
        }
        emitter.setCancellable { job.cancel() }
    }
}
