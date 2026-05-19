package com.inqulab.heartkaroo.efficiency

/**
 * Cardiac cost — average heart rate divided by average power over the
 * last 30 minutes (bpm/W).
 *
 * The arithmetic inverse of Efficiency Factor, but more intuitive for
 * day-to-day comparison: "how many beats per watt am I paying right
 * now?" Lower is better. Useful complement to EF — same information,
 * different mental model.
 *
 * Returns null until warmup elapses and average power is non-zero.
 */
class CardiacCostCalculator(
    private val windowMs: Long = 30 * 60 * 1000L,
    private val warmupMs: Long = 10 * 60 * 1000L,
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
        var pSum = 0.0; var hSum = 0.0
        for (s in samples) { pSum += s.power; hSum += s.hr }
        val avgP = pSum / samples.size
        if (avgP <= 0.0) return null
        return ((hSum / samples.size) / avgP).toFloat()
    }

    @Synchronized
    fun reset() { samples.clear() }
}
