package com.inqulab.heartkaroo.decoupling

/**
 * Watches the decoupling % stream and latches the elapsed-time at which
 * decoupling first crossed (and stayed above) a configurable threshold.
 *
 * The "pop" — the moment cardiac drift breaks above your aerobic ceiling
 * — is the metric most riders actually care about. Showing it as a
 * latching time-stamp on the head unit is more useful in the moment than
 * watching the raw % crawl upward.
 *
 * Output (`getPopMinutes`) reports null until the pop happens, then the
 * minutes-into-the-current-stream that the threshold was first crossed,
 * which stays constant for the rest of the ride.
 *
 * To avoid latching on a single noisy spike, the threshold must be
 * exceeded for at least `confirmMs` of continuous samples.
 */
class CardiacPopDetector(
    private val thresholdPct: Double = 5.0,
    private val confirmMs: Long = 30_000L,
) {
    private var startTimeMs: Long = -1L
    private var crossingStartedAt: Long = -1L
    private var popTimeMs: Long = -1L

    @Synchronized
    fun add(timeMs: Long, decouplingPct: Double?) {
        if (startTimeMs < 0L) startTimeMs = timeMs
        if (popTimeMs >= 0L) return
        if (decouplingPct == null) return
        if (decouplingPct >= thresholdPct) {
            if (crossingStartedAt < 0L) crossingStartedAt = timeMs
            if (timeMs - crossingStartedAt >= confirmMs) popTimeMs = crossingStartedAt
        } else {
            crossingStartedAt = -1L
        }
    }

    @Synchronized
    fun getPopMinutes(): Float? {
        if (popTimeMs < 0L || startTimeMs < 0L) return null
        return ((popTimeMs - startTimeMs) / 60_000.0).toFloat()
    }

    @Synchronized
    fun reset() {
        startTimeMs = -1L
        crossingStartedAt = -1L
        popTimeMs = -1L
    }
}
