package com.inqulab.heartkaroo.hrv

import kotlin.math.abs

/**
 * Percentage of successive RR-interval differences exceeding 50 ms (pNN50).
 *
 * A classic parasympathetic / vagal-tone marker. Drops sharply with
 * exercise intensity. Computed over a sliding window of beats.
 */
class Pnn50Calculator(private val windowSize: Int = 60) {

    private val rrIntervals = ArrayDeque<Int>()

    @Synchronized
    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        rrIntervals.addLast(rrMs)
        if (rrIntervals.size > windowSize) rrIntervals.removeFirst()
    }

    @Synchronized
    fun getPnn50(): Float? {
        if (rrIntervals.size < 2) return null
        var nn50 = 0
        var total = 0
        var prev = -1
        for (v in rrIntervals) {
            if (prev >= 0) {
                if (abs(v - prev) > 50) nn50++
                total++
            }
            prev = v
        }
        if (total == 0) return null
        return (nn50.toFloat() / total) * 100f
    }

    @Synchronized
    fun reset() { rrIntervals.clear() }
}
