package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Custom data field exposed to Karoo as "DFA α1".
 *
 * Reads the α1 flow maintained by PolarBleManager, recomputed each time
 * a new RR interval arrives from the BLE Heart Rate Measurement notification.
 */
class DfaAlpha1DataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "dfa_alpha1"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        emitter.onNext(StreamState.NotAvailable)
        val job = CoroutineScope(Dispatchers.IO).launch {
            bleManager.dfaAlpha1Flow.collect { alpha ->
                if (alpha == null) {
                    emitter.onNext(StreamState.NotAvailable)
                } else {
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to alpha.toDouble()),
                            )
                        )
                    )
                }
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
