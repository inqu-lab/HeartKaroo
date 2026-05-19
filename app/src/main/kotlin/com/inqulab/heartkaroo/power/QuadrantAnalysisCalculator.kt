package com.inqulab.heartkaroo.power

import kotlin.math.PI

/**
 * Coggan quadrant analysis.
 *
 * Splits each (power, cadence) sample into one of four quadrants based
 * on its Force-per-Stroke (FP, in N) and Circumferential Pedal Velocity
 * (CPV, in m/s):
 *
 *   Quadrant I   (high FP, high CPV): aerobic/threshold work
 *   Quadrant II  (high FP, low CPV):  neuromuscular (grinder)
 *   Quadrant III (low FP, low CPV):   recovery
 *   Quadrant IV  (low FP, high CPV):  spinning / sprint
 *
 * The split lines pass through the rider's FTP-at-90-rpm point. The
 * field reports 1..4 as the dominant quadrant over the last 60 s, so
 * head units that don't support text values can still display it.
 */
class QuadrantAnalysisCalculator(
    private val ftpW: Double,
    private val crankLengthM: Double = 0.175,
    private val windowMs: Long = 60_000L,
) {
    private data class Sample(val timeMs: Long, val quadrant: Int)

    private val window = ArrayDeque<Sample>()

    /** Reference FP at FTP & 90 rpm. */
    private val refFp: Double
        get() {
            val refCpv = 2.0 * PI * crankLengthM * (90.0 / 60.0)
            return ftpW / refCpv
        }

    private val refCpv: Double
        get() = 2.0 * PI * crankLengthM * (90.0 / 60.0)

    @Synchronized
    fun add(timeMs: Long, powerW: Double, cadenceRpm: Double) {
        if (powerW < 0.0 || cadenceRpm <= 0.0) return
        val cpv = 2.0 * PI * crankLengthM * (cadenceRpm / 60.0)
        if (cpv <= 0.0) return
        val fp = powerW / cpv
        val highFp = fp >= refFp
        val highCpv = cpv >= refCpv
        val q = when {
            highFp && highCpv -> 1
            highFp && !highCpv -> 2
            !highFp && !highCpv -> 3
            else -> 4
        }
        window.addLast(Sample(timeMs, q))
        val cutoff = timeMs - windowMs
        while (window.isNotEmpty() && window.first().timeMs < cutoff) window.removeFirst()
    }

    @Synchronized
    fun dominantQuadrant(): Int? {
        if (window.isEmpty()) return null
        val counts = IntArray(5)
        for (s in window) counts[s.quadrant]++
        var bestQ = 1
        var bestC = counts[1]
        for (q in 2..4) if (counts[q] > bestC) { bestC = counts[q]; bestQ = q }
        return bestQ
    }

    @Synchronized
    fun reset() { window.clear() }
}
