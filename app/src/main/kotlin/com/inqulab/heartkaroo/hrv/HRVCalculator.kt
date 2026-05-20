package com.inqulab.heartkaroo.hrv

import kotlin.math.sqrt

class HRVCalculator(private val windowSize: Int = 30) {

    private val rrIntervals = ArrayDeque<Int>()

    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        rrIntervals.addLast(rrMs)
        if (rrIntervals.size > windowSize) rrIntervals.removeFirst()
    }

    fun getRmssd(): Float {
        if (rrIntervals.size < 2) return 0f
        var sumSquaredDiffs = 0.0
        for (i in 1 until rrIntervals.size) {
            val diff = (rrIntervals[i] - rrIntervals[i - 1]).toDouble()
            sumSquaredDiffs += diff * diff
        }
        return sqrt(sumSquaredDiffs / (rrIntervals.size - 1)).toFloat()
    }

    val hasData: Boolean get() = rrIntervals.size >= 2

    fun reset() { rrIntervals.clear() }
}
