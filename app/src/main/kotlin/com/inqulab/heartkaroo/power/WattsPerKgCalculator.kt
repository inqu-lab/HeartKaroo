package com.inqulab.heartkaroo.power

/**
 * Live power-to-weight: mean power over a short smoothing window divided by
 * rider weight. The 3 s default matches the "3s power" display convention on
 * head units — raw 1 Hz power is too jumpy to read on a climb.
 */
class WattsPerKgCalculator(
    private val weightKg: Float,
    private val windowMs: Long = 3_000L,
) {

    private data class Sample(val timeMs: Long, val power: Double)

    private val window = ArrayDeque<Sample>()

    @Synchronized
    fun add(timeMs: Long, power: Double): Float? {
        if (!power.isFinite() || power < 0.0) return current()
        window.addLast(Sample(timeMs, power))
        val cutoff = timeMs - windowMs
        while (window.isNotEmpty() && window.first().timeMs < cutoff) {
            window.removeFirst()
        }
        return current()
    }

    @Synchronized
    fun current(): Float? {
        if (weightKg <= 0f || window.isEmpty()) return null
        var sum = 0.0
        for (s in window) sum += s.power
        return (sum / window.size / weightKg).toFloat()
    }
}
