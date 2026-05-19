package com.inqulab.heartkaroo.decoupling

/**
 * Rolling Pw:Hr aerobic-decoupling calculator (Friel method).
 *
 * Decoupling % = (firstHalfRatio - secondHalfRatio) / firstHalfRatio * 100,
 * where ratio = mean(power) / mean(heartRate) over a half-window.
 *
 * Positive values mean heart rate has drifted up relative to power
 * (cardiac drift). Under ~5% is generally considered well-coupled.
 */
class DecouplingCalculator(
    private val windowMs: Long = 30 * 60 * 1000L,
    private val warmupMs: Long = 10 * 60 * 1000L,
) {
    private data class Sample(val timeMs: Long, val power: Double, val hr: Double)

    private val samples = ArrayDeque<Sample>()

    @Synchronized
    fun add(timeMs: Long, power: Double, hr: Double): Double? {
        if (power > 0.0 && hr > 0.0) {
            samples.addLast(Sample(timeMs, power, hr))
        }
        val cutoff = timeMs - windowMs
        while (samples.isNotEmpty() && samples.first().timeMs < cutoff) {
            samples.removeFirst()
        }
        return current()
    }

    @Synchronized
    fun current(): Double? {
        if (samples.size < 2) return null
        val span = samples.last().timeMs - samples.first().timeMs
        if (span < warmupMs) return null

        val midTime = samples.first().timeMs + span / 2
        var p1 = 0.0; var h1 = 0.0; var n1 = 0
        var p2 = 0.0; var h2 = 0.0; var n2 = 0
        for (s in samples) {
            if (s.timeMs <= midTime) {
                p1 += s.power; h1 += s.hr; n1++
            } else {
                p2 += s.power; h2 += s.hr; n2++
            }
        }
        if (n1 == 0 || n2 == 0) return null
        val r1 = (p1 / n1) / (h1 / n1)
        val r2 = (p2 / n2) / (h2 / n2)
        if (r1 == 0.0) return null
        return (r1 - r2) / r1 * 100.0
    }

    @Synchronized
    fun reset() {
        samples.clear()
    }
}
