package com.inqulab.heartkaroo.karoo

import android.util.Log
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun KarooSystemService.streamDataFlow(dataTypeId: String): Flow<StreamState> = callbackFlow {
    // TEMP diagnostics: confirm whether the Karoo system is connected and
    // whether it actually delivers stream values for this data type.
    Log.i("HeartKaroo", "streamDataFlow subscribe '$dataTypeId' connected=$connected")
    var logged = false
    val consumerId = addConsumer<OnStreamState>(
        OnStreamState.StartStreaming(dataTypeId),
    ) { event ->
        if (!logged) {
            Log.i("HeartKaroo", "streamDataFlow '$dataTypeId' first event: ${event.state}")
            logged = true
        }
        trySend(event.state)
    }
    awaitClose { removeConsumer(consumerId) }
}
