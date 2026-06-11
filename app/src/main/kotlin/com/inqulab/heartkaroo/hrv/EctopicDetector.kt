package com.inqulab.heartkaroo.hrv

import kotlin.math.abs

/**
 * Crude detector for irregular RR intervals (likely ectopic / premature
 * beats) over a sliding window.
 *
 * Heuristic: an RR interval whose absolute deviation from the previous
 * accepted interval exceeds 20 % is flagged as suspicious. The reported
 * value is the number of such events per minute over the window's
 * timespan.
 *
 * This is NOT a clinical diagnostic and explicitly does not claim to
 * detect arrhythmias such as AFib. Use it as a "something looks unusual"
 * flag, not as medical advice.
 */
class EctopicDetector(
    private val windowSize: Int = 120,
    private val thresholdFrac: Double = 0.20,
    private val maxConsecutiveFlags: Int = 4,
) {
    private data class Beat(val rrMs: Int, val flagged: Boolean)

    private val window = ArrayDeque<Beat>()
    private var prevAccepted: Int = -1
    private var consecutiveFlags = 0

    @Synchronized
    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        var flagged = if (prevAccepted < 0) false
        else abs(rrMs - prevAccepted) > prevAccepted * thresholdFrac
        if (flagged && ++consecutiveFlags >= maxConsecutiveFlags) {
            // A sustained run of deviations is a genuine baseline shift (hard
            // surge, stream resuming after a dropout), not minutes of ectopy:
            // re-sync the reference instead of flagging every beat against a
            // stale one. (Same re-sync RrArtifactCorrector uses.)
            flagged = false
            consecutiveFlags = 0
            prevAccepted = rrMs
        }
        window.addLast(Beat(rrMs, flagged))
        if (window.size > windowSize) window.removeFirst()
        if (!flagged) {
            consecutiveFlags = 0
            prevAccepted = rrMs
        }
    }

    /**
     * Suspicious-beat rate, in events per minute, over the current window.
     * Returns null until the window has enough data (≥ 30 beats).
     */
    @Synchronized
    fun getEventsPerMin(): Float? {
        if (window.size < 30) return null
        var flagged = 0
        var totalMs = 0L
        for (b in window) {
            if (b.flagged) flagged++
            totalMs += b.rrMs
        }
        if (totalMs <= 0L) return null
        return (flagged * 60_000f) / totalMs.toFloat()
    }

    @Synchronized
    fun reset() {
        window.clear()
        prevAccepted = -1
        consecutiveFlags = 0
    }
}
