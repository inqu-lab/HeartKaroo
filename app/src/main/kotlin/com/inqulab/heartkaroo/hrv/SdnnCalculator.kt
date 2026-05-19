package com.inqulab.heartkaroo.hrv

import kotlin.math.sqrt

/**
 * Standard deviation of NN (RR) intervals over a sliding window.
 *
 * SDNN reflects overall HRV (both short- and longer-term variability).
 * Window default 60 beats — short enough to track within-ride changes,
 * long enough to be stable.
 */
class SdnnCalculator(private val windowSize: Int = 60) {

    private val rrIntervals = ArrayDeque<Int>()

    @Synchronized
    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        rrIntervals.addLast(rrMs)
        if (rrIntervals.size > windowSize) rrIntervals.removeFirst()
    }

    @Synchronized
    fun getSdnn(): Float? {
        if (rrIntervals.size < 2) return null
        var sum = 0.0
        for (v in rrIntervals) sum += v
        val mean = sum / rrIntervals.size
        var ssq = 0.0
        for (v in rrIntervals) {
            val d = v - mean
            ssq += d * d
        }
        return sqrt(ssq / (rrIntervals.size - 1)).toFloat()
    }

    @Synchronized
    fun reset() { rrIntervals.clear() }
}
