package com.inqulab.heartkaroo.karoo

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    val consumerId = addConsumer<OnStreamState>(
        OnStreamState.StartStreaming(dataTypeId),
    ) { event ->
        trySend(event.state)
    }
    awaitClose { removeConsumer(consumerId) }
}
