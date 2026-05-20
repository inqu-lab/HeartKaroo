package com.inqulab.heartkaroo.hrv

import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Thin DataType implementation that forwards each non-null value from a
 * Float? flow to Karoo. Used for all HRV-derived single-value fields
 * (RMSSD, stress, DFA α1, SDNN, pNN50, SD1/SD2/ratio, respiratory rate,
 * ectopic rate).
 */
class HrvFlowDataType(
    extensionId: String,
    typeId: String,
    private val source: Flow<Float?>,
) : DataTypeImpl(extensionId, typeId) {

    override fun startStream(emitter: Emitter<StreamState>) {
        // Searching (not NotAvailable) while the strap connects / the HRV
        // window fills. NotAvailable is the state Karoo renders as "no sensor".
        emitter.onNext(StreamState.Searching)
        val job = CoroutineScope(Dispatchers.IO).launch {
            source.collect { value ->
                if (value == null) {
                    emitter.onNext(StreamState.Searching)
                } else {
                    emitter.onNext(
                        StreamState.Streaming(
                            DataPoint(
                                dataTypeId = dataTypeId,
                                values = mapOf(DataType.Field.SINGLE to value.toDouble()),
                            )
                        )
                    )
                }
            }
        }
        emitter.setCancellable { job.cancel() }
    }
}
