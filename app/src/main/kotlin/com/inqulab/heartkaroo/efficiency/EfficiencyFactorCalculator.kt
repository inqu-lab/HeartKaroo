package com.inqulab.heartkaroo.efficiency

import kotlin.math.pow

/**
 * Coggan Efficiency Factor (EF) = Normalized Power / average heart rate,
 * computed over a rolling window (default 30 min).
 *
 * Normalized Power (Coggan):
 *   1. Take a 30-second rolling average of power.
 *   2. Raise each value to the 4th power.
 *   3. Take the mean.
 *   4. Take the 4th root.
 *
 * Higher EF means more watts per beat — better aerobic fitness when
 * compared across rides at similar intensity. Drift downward within a
 * single ride indicates fatigue / decoupling.
 *
 * Returns null until the warmup window has elapsed.
 */
class EfficiencyFactorCalculator(
    private val windowMs: Long = 30 * 60 * 1000L,
    private val warmupMs: Long = 10 * 60 * 1000L,
    private val npSmoothingMs: Long = 30_000L,
) {
    private data class Sample(val timeMs: Long, val power: Double, val hr: Double)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(timeMs: Long, power: Double, hr: Double): Float? {
        if (power >= 0.0 && hr > 0.0) samples.addLast(Sample(timeMs, power, hr))
        val cutoff = timeMs - windowMs
        while (samples.isNotEmpty() && samples.first().timeMs < cutoff) samples.removeFirst()
        return current()
    }

    @Synchronized
    fun current(): Float? {
        if (samples.size < 2) return null
        val span = samples.last().timeMs - samples.first().timeMs
        if (span < warmupMs) return null
        val np = normalizedPower() ?: return null
        var hrSum = 0.0
        for (s in samples) hrSum += s.hr
        val avgHr = hrSum / samples.size
        if (avgHr <= 0.0) return null
        return (np / avgHr).toFloat()
    }

    private fun normalizedPower(): Double? {
        if (samples.isEmpty()) return null
        // Sliding 30-s mean of power, then 4th-power mean, then 4th root.
        var sumP4 = 0.0
        var count = 0
        var lo = 0
        val list = samples.toList()
        for (hi in list.indices) {
            val hiTime = list[hi].timeMs
            while (lo < hi && list[lo].timeMs < hiTime - npSmoothingMs) lo++
            var subSum = 0.0
            var subCount = 0
            for (i in lo..hi) { subSum += list[i].power; subCount++ }
            if (subCount == 0) continue
            val avg = subSum / subCount
            sumP4 += avg.pow(4)
            count++
        }
        if (count == 0) return null
        return (sumP4 / count).pow(0.25)
    }

    @Synchronized
    fun reset() { samples.clear() }
}
