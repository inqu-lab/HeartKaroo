package com.inqulab.heartkaroo

import com.inqulab.heartkaroo.hrv.PolarBleManager
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared helpers for the on-device (Karoo) integration tests. These wait on
 * real callbacks/streams with timeouts so a test can skip (via assumeTrue)
 * when the precondition — a Karoo connection, an active ride, a strap in
 * range — isn't present, rather than hanging or failing.
 */

/** Block until the Karoo system service reports connected, or time out. */
fun KarooSystemService.connectBlocking(timeoutSec: Long = 8): Boolean {
    val latch = CountDownLatch(1)
    connect { connected -> if (connected) latch.countDown() }
    return latch.await(timeoutSec, TimeUnit.SECONDS)
}

/** First value from [flow] matching [predicate] within [timeoutMs], else null. */
fun <T> awaitFlow(
    timeoutMs: Long,
    flow: Flow<T>,
    predicate: (T) -> Boolean = { true },
): T? = runBlocking { withTimeoutOrNull(timeoutMs) { flow.first { predicate(it) } } }

/** Scan for the first BLE HRM in range within [timeoutSec], else null. */
fun PolarBleManager.firstDevice(timeoutSec: Long = 15): PolarBleManager.DiscoveredDevice? {
    val ref = AtomicReference<PolarBleManager.DiscoveredDevice?>()
    val latch = CountDownLatch(1)
    val stop = startDeviceScan { device -> if (ref.compareAndSet(null, device)) latch.countDown() }
    try {
        latch.await(timeoutSec, TimeUnit.SECONDS)
    } finally {
        stop()
    }
    return ref.get()
}
