package com.inqulab.heartkaroo.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val consumerId = addConsumer<OnStreamState>(
        OnStreamState.StartStreaming(dataTypeId),
    ) { event ->
        trySend(event.state)
    }
    awaitClose { removeConsumer(consumerId) }
}

/**
 * Bridges a Float? source (e.g. a RidePowerEngine StateFlow) to a Karoo data
 * field: null -> Searching, a value -> Streaming. The source lives independently
 * of this stream, so the field showing or hiding never resets the underlying
 * accumulation. Returns a cancel handle for the collector.
 */
fun streamFloatState(
    source: Flow<Float?>,
    dataTypeId: String,
    emitter: Emitter<StreamState>,
): () -> Unit {
    val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    val job = scope.launch {
        source.collect { v ->
            emitter.onNext(
                if (v == null) {
                    StreamState.Searching
                } else {
                    StreamState.Streaming(
                        DataPoint(dataTypeId, mapOf(DataType.Field.SINGLE to v.toDouble())),
                    )
                },
            )
        }
    }
    return { job.cancel(); scope.cancel() }
}
