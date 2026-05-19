package com.inqulab.heartkaroo.climb

/**
 * VAM — Vertical Ascent Meters per hour.
 *
 * The standard climbing-speed metric. Computed as the rate of altitude
 * gain over a sliding window (default 60 s). Drops to zero when not
 * climbing. Negative values (descents) are clamped to 0 — VAM
 * conventionally only counts ascent.
 */
class VamCalculator(private val windowMs: Long = 60_000L) {

    private data class Sample(val timeMs: Long, val elevM: Double)

    private val window = ArrayDeque<Sample>()

    @Synchronized
    fun add(timeMs: Long, elevationM: Double): Float? {
        if (!elevationM.isFinite()) return current()
        window.addLast(Sample(timeMs, elevationM))
        val cutoff = timeMs - windowMs
        while (window.size > 2 && window.first().timeMs < cutoff) window.removeFirst()
        return current()
    }

    @Synchronized
    fun current(): Float? {
        if (window.size < 2) return null
        val first = window.first()
        val last = window.last()
        val dtSec = (last.timeMs - first.timeMs) / 1000.0
        if (dtSec < 10.0) return null
        val dElev = last.elevM - first.elevM
        if (dElev <= 0.0) return 0f
        return ((dElev / dtSec) * 3600.0).toFloat()
    }

    @Synchronized
    fun reset() { window.clear() }
}
