package com.inqulab.heartkaroo.power

import kotlin.math.pow

/**
 * Rolling Normalized Power (Coggan) + Average Power calculator.
 *
 * Normalized Power = 4th root of mean of (30-s rolling power)^4.
 *
 * The default 60-minute window keeps NP stable while still tracking
 * intensity changes through the ride. Used by Variability Index,
 * Intensity Factor and TSS data fields.
 */
class NormalizedPowerCalculator(
    private val windowMs: Long = 60 * 60 * 1000L,
    private val smoothingMs: Long = 30_000L,
    private val minSamples: Int = 30,
) {
    private data class Sample(val timeMs: Long, val power: Double)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(timeMs: Long, power: Double) {
        if (power < 0.0 || !power.isFinite()) return
        samples.addLast(Sample(timeMs, power))
        val cutoff = timeMs - windowMs
        while (samples.isNotEmpty() && samples.first().timeMs < cutoff) samples.removeFirst()
    }

    @Synchronized
    fun normalizedPower(): Float? {
        if (samples.size < minSamples) return null
        var sumP4 = 0.0
        var count = 0
        var lo = 0
        val list = samples.toList()
        for (hi in list.indices) {
            val hiTime = list[hi].timeMs
            while (lo < hi && list[lo].timeMs < hiTime - smoothingMs) lo++
            var subSum = 0.0
            var subCount = 0
            for (i in lo..hi) { subSum += list[i].power; subCount++ }
            if (subCount == 0) continue
            sumP4 += (subSum / subCount).pow(4)
            count++
        }
        if (count == 0) return null
        return (sumP4 / count).pow(0.25).toFloat()
    }

    @Synchronized
    fun averagePower(): Float? {
        if (samples.isEmpty()) return null
        var sum = 0.0
        for (s in samples) sum += s.power
        return (sum / samples.size).toFloat()
    }

    @Synchronized
    fun elapsedSec(): Double {
        if (samples.size < 2) return 0.0
        return (samples.last().timeMs - samples.first().timeMs) / 1000.0
    }

    @Synchronized
    fun reset() { samples.clear() }
}
