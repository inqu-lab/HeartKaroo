package com.inqulab.heartkaroo.hrv

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Detrended Fluctuation Analysis short-term scaling exponent (DFA α1)
 * over a sliding window of RR intervals.
 *
 * α1 ≈ 1.0 indicates scale-free RR fluctuations (low aerobic intensity).
 * α1 ≈ 0.5 indicates random / uncorrelated fluctuations (high intensity).
 * α1 ≈ 0.75 is widely used as a non-invasive aerobic-threshold marker
 * (Rogell et al., 2021).
 *
 * Implementation follows the standard DFA procedure:
 *   1. Integrate RR after subtracting the mean.
 *   2. For each box size n in [minBox, maxBox], split the integrated
 *      series into non-overlapping boxes, fit a local linear trend in
 *      each box, accumulate squared residuals, and take F(n) = RMS.
 *   3. α1 is the slope of log F(n) vs log n.
 */
class DfaAlpha1Calculator(
    private val minSamples: Int = 120,
    private val maxSamples: Int = 480,
    private val minBox: Int = 4,
    private val maxBox: Int = 16,
) {
    private val rrIntervals = ArrayDeque<Int>()

    @Synchronized
    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        rrIntervals.addLast(rrMs)
        while (rrIntervals.size > maxSamples) rrIntervals.removeFirst()
    }

    @Synchronized
    fun getAlpha1(): Float? {
        if (rrIntervals.size < minSamples) return null
        val rr = DoubleArray(rrIntervals.size)
        var sum = 0.0
        var i = 0
        for (v in rrIntervals) { rr[i] = v.toDouble(); sum += rr[i]; i++ }
        val mean = sum / rr.size
        val y = DoubleArray(rr.size)
        var cum = 0.0
        for (k in rr.indices) { cum += rr[k] - mean; y[k] = cum }

        val logN = ArrayList<Double>(maxBox - minBox + 1)
        val logF = ArrayList<Double>(maxBox - minBox + 1)
        for (boxSize in minBox..maxBox) {
            val numBoxes = y.size / boxSize
            if (numBoxes < 2) continue
            var sumSqResid = 0.0
            for (b in 0 until numBoxes) {
                val start = b * boxSize
                val (slope, intercept) = linearFit(y, start, boxSize)
                for (kk in 0 until boxSize) {
                    val resid = y[start + kk] - (slope * kk + intercept)
                    sumSqResid += resid * resid
                }
            }
            val totalSamples = numBoxes * boxSize
            val fn = sqrt(sumSqResid / totalSamples)
            if (fn > 0.0) {
                logN.add(ln(boxSize.toDouble()))
                logF.add(ln(fn))
            }
        }
        if (logN.size < 3) return null
        return ordinaryLeastSquaresSlope(logN, logF).toFloat()
    }

    @Synchronized
    fun reset() { rrIntervals.clear() }

    val hasData: Boolean get() = rrIntervals.size >= minSamples

    private fun linearFit(y: DoubleArray, start: Int, length: Int): Pair<Double, Double> {
        var sumX = 0.0; var sumY = 0.0; var sumXY = 0.0; var sumXX = 0.0
        for (k in 0 until length) {
            val xv = k.toDouble()
            val yv = y[start + k]
            sumX += xv; sumY += yv; sumXY += xv * yv; sumXX += xv * xv
        }
        val nD = length.toDouble()
        val denom = nD * sumXX - sumX * sumX
        if (denom == 0.0) return 0.0 to (sumY / nD)
        val slope = (nD * sumXY - sumX * sumY) / denom
        val intercept = (sumY - slope * sumX) / nD
        return slope to intercept
    }

    private fun ordinaryLeastSquaresSlope(x: List<Double>, y: List<Double>): Double {
        val nD = x.size.toDouble()
        var sumX = 0.0; var sumY = 0.0; var sumXY = 0.0; var sumXX = 0.0
        for (idx in x.indices) {
            val xv = x[idx]; val yv = y[idx]
            sumX += xv; sumY += yv; sumXY += xv * yv; sumXX += xv * xv
        }
        val denom = nD * sumXX - sumX * sumX
        if (denom == 0.0) return 0.0
        return (nD * sumXY - sumX * sumY) / denom
    }
}
