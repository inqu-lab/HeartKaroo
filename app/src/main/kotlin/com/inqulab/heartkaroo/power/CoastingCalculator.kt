package com.inqulab.heartkaroo.power

/**
 * Tracks the fraction of ride time spent coasting (power below a small
 * threshold, default 5 W). Useful pacing-self-awareness metric: most
 * riders underestimate how much of a ride is spent freewheeling.
 *
 * Reported as a 0..100 % value once at least `minWarmupMs` of samples
 * have been seen, so the number doesn't flap wildly on the first few
 * seconds.
 */
class CoastingCalculator(
    private val coastWatts: Double = 5.0,
    private val minWarmupMs: Long = 60_000L,
    // A gap longer than this (sensor dropout, ride pause) isn't riding time:
    // crediting it to the pre-gap coasting state would skew the percentage.
    private val maxGapMs: Long = 10_000L,
) {
    private var startMs: Long = -1L
    private var lastTimeMs: Long = -1L
    private var lastCoasting: Boolean = false
    private var coastingMs: Long = 0L
    private var totalMs: Long = 0L

    @Synchronized
    fun add(timeMs: Long, power: Double): Float? {
        if (!power.isFinite()) return current()
        if (startMs < 0L) startMs = timeMs
        if (lastTimeMs >= 0L && timeMs > lastTimeMs && timeMs - lastTimeMs <= maxGapMs) {
            val dt = timeMs - lastTimeMs
            totalMs += dt
            if (lastCoasting) coastingMs += dt
        }
        lastTimeMs = timeMs
        lastCoasting = power < coastWatts
        return current()
    }

    @Synchronized
    fun current(): Float? {
        if (totalMs < minWarmupMs) return null
        return (coastingMs.toDouble() / totalMs * 100.0).toFloat()
    }

    @Synchronized
    fun reset() {
        startMs = -1L
        lastTimeMs = -1L
        lastCoasting = false
        coastingMs = 0L
        totalMs = 0L
    }
}
