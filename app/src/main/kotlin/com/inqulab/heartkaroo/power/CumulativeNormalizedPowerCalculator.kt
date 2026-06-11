package com.inqulab.heartkaroo.power

import kotlin.math.pow

/**
 * Whole-ride Normalized Power (Coggan) for cumulative metrics like TSS.
 *
 * Unlike [NormalizedPowerCalculator], nothing is ever evicted: the 30-s rolling
 * mean is maintained incrementally and only its 4th powers are accumulated, so
 * memory stays bounded by the smoothing window while NP and elapsed time cover
 * the entire ride. (A windowed calculator capped TSS at the window length —
 * a 6-h ride reported the TSS of its last 4 h, and the value could even drop
 * as hard early riding scrolled out of the window. TSS is cumulative and can
 * only grow.)
 */
class CumulativeNormalizedPowerCalculator(
    private val smoothingMs: Long = 30_000L,
    private val minSamples: Int = 30,
) {
    private data class Sample(val timeMs: Long, val power: Double)

    // Only the samples inside the smoothing window are retained.
    private val recent = ArrayDeque<Sample>()
    private var recentSum = 0.0
    private var sumP4 = 0.0
    private var count = 0
    private var firstTimeMs = -1L
    private var lastTimeMs = -1L

    @Synchronized
    fun add(timeMs: Long, power: Double) {
        if (power < 0.0 || !power.isFinite()) return
        recent.addLast(Sample(timeMs, power))
        recentSum += power
        val cutoff = timeMs - smoothingMs
        while (recent.isNotEmpty() && recent.first().timeMs < cutoff) {
            recentSum -= recent.removeFirst().power
        }
        sumP4 += (recentSum / recent.size).pow(4)
        count++
        if (firstTimeMs < 0) firstTimeMs = timeMs
        lastTimeMs = timeMs
    }

    @Synchronized
    fun normalizedPower(): Float? {
        if (count < minSamples) return null
        return (sumP4 / count).pow(0.25).toFloat()
    }

    @Synchronized
    fun elapsedSec(): Double {
        if (firstTimeMs < 0 || lastTimeMs <= firstTimeMs) return 0.0
        return (lastTimeMs - firstTimeMs) / 1000.0
    }

    @Synchronized
    fun reset() {
        recent.clear()
        recentSum = 0.0
        sumP4 = 0.0
        count = 0
        firstTimeMs = -1L
        lastTimeMs = -1L
    }
}
