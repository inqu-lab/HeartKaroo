package com.inqulab.heartkaroo.hrv

import kotlin.math.sqrt

/**
 * Poincaré-plot descriptors SD1, SD2 and their ratio over a sliding RR
 * window.
 *
 *  SD1 = sqrt(0.5 * SDSD²)              short-term variability
 *  SD2 = sqrt(2*SDNN² - 0.5*SDSD²)      long-term variability
 *  Ratio = SD1 / SD2                    parasympathetic vs sympathetic balance
 */
class PoincareCalculator(private val windowSize: Int = 60) {

    data class Result(val sd1: Float, val sd2: Float, val ratio: Float)

    private val rrIntervals = ArrayDeque<Int>()

    @Synchronized
    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        rrIntervals.addLast(rrMs)
        if (rrIntervals.size > windowSize) rrIntervals.removeFirst()
    }

    @Synchronized
    fun getResult(): Result? {
        if (rrIntervals.size < 3) return null
        // SDNN
        var sum = 0.0
        for (v in rrIntervals) sum += v
        val mean = sum / rrIntervals.size
        var ssq = 0.0
        for (v in rrIntervals) {
            val d = v - mean
            ssq += d * d
        }
        val sdnn2 = ssq / (rrIntervals.size - 1)
        // SDSD = stdev of successive differences (n-1 denom over the n-1 diffs)
        var dsum = 0.0
        var dcount = 0
        var prev = -1
        for (v in rrIntervals) {
            if (prev >= 0) { dsum += (v - prev); dcount++ }
            prev = v
        }
        val dmean = dsum / dcount
        var dssq = 0.0
        prev = -1
        for (v in rrIntervals) {
            if (prev >= 0) {
                val dd = (v - prev) - dmean
                dssq += dd * dd
            }
            prev = v
        }
        if (dcount < 2) return null
        val sdsd2 = dssq / (dcount - 1)
        val sd1Sq = 0.5 * sdsd2
        val sd2Sq = 2.0 * sdnn2 - 0.5 * sdsd2
        if (sd1Sq < 0.0 || sd2Sq <= 0.0) return null
        val sd1 = sqrt(sd1Sq)
        val sd2 = sqrt(sd2Sq)
        return Result(sd1.toFloat(), sd2.toFloat(), (sd1 / sd2).toFloat())
    }

    @Synchronized
    fun reset() { rrIntervals.clear() }
}
