package com.inqulab.heartkaroo

import android.os.Build
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

/**
 * True on an Android emulator (incl. the CI `connectedDebugAndroidTest`
 * runner). These tests need real Karoo / BLE hardware, so they skip up front
 * on an emulator instead of hanging on a connection that never comes or
 * touching the Polar SDK on a device with no real radio.
 */
fun isEmulator(): Boolean {
    val fp = Build.FINGERPRINT ?: ""
    return fp.startsWith("generic") ||
        fp.startsWith("unknown") ||
        fp.contains("generic") ||
        Build.MODEL.contains("Emulator") ||
        Build.MODEL.contains("Android SDK built for") ||
        Build.MANUFACTURER.contains("Genymotion") ||
        Build.PRODUCT.contains("sdk") ||
        Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu")
}

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
