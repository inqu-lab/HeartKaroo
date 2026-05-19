package com.inqulab.heartkaroo.hrv

/**
 * Respiratory rate (breaths / min) derived from respiratory sinus
 * arrhythmia (RSA) — the oscillation of RR intervals with breathing.
 *
 * Algorithm:
 *   1. Maintain a sliding window of RR intervals (default ~30 beats).
 *   2. Compute centered residuals: RR(i) - rolling_mean.
 *   3. Count zero-crossings of the residual series; each pair of
 *      crossings ≈ one breath cycle.
 *   4. Total time covered by the window = sum of RR intervals (ms).
 *      breaths/min = (crossings / 2) / (windowMs/60_000).
 *
 * Caveats:
 *   - RSA collapses at high exercise intensity, so accuracy degrades.
 *   - Returns null until at least 12 intervals have been collected.
 *   - Values clamped to a sane 6..40 breaths/min physiological range.
 */
class RespiratoryRateCalculator(private val windowSize: Int = 30) {

    private val rrIntervals = ArrayDeque<Int>()

    @Synchronized
    fun addInterval(rrMs: Int) {
        if (rrMs < 300 || rrMs > 2000) return
        rrIntervals.addLast(rrMs)
        if (rrIntervals.size > windowSize) rrIntervals.removeFirst()
    }

    @Synchronized
    fun getBreathsPerMin(): Float? {
        if (rrIntervals.size < 12) return null
        var sum = 0L
        for (v in rrIntervals) sum += v
        val mean = sum.toDouble() / rrIntervals.size
        var crossings = 0
        var prevSign = 0
        for (v in rrIntervals) {
            val resid = v - mean
            val sign = when {
                resid > 0 -> 1
                resid < 0 -> -1
                else -> 0
            }
            if (sign != 0 && prevSign != 0 && sign != prevSign) crossings++
            if (sign != 0) prevSign = sign
        }
        val durationSec = sum / 1000.0
        if (durationSec <= 0.0) return null
        val breaths = crossings / 2.0
        val bpm = (breaths / durationSec) * 60.0
        if (bpm < 6.0 || bpm > 40.0) return null
        return bpm.toFloat()
    }

    @Synchronized
    fun reset() { rrIntervals.clear() }
}
