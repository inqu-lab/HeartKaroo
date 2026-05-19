package com.inqulab.heartkaroo.power

/**
 * Mean Maximal Power for a fixed duration over the current ride —
 * the highest sustained mean power achieved for `windowMs` so far.
 *
 * Implemented with a monotonic deque (Brodal-style sliding window mean)
 * over a rolling time window. Each instance tracks one duration; the
 * Karoo exposes five fields (5 s, 1 min, 5 min, 20 min, 60 min).
 */
class MmpCalculator(val windowMs: Long) {

    private data class Sample(val timeMs: Long, val power: Double)

    private val window = ArrayDeque<Sample>()
    private var bestMean: Double = 0.0
    private var bestKnown: Boolean = false
    private var totalSpannedMs: Long = 0L

    @Synchronized
    fun add(timeMs: Long, power: Double): Float? {
        if (!power.isFinite() || power < 0.0) return current()
        window.addLast(Sample(timeMs, power))
        val cutoff = timeMs - windowMs
        while (window.isNotEmpty() && window.first().timeMs < cutoff) {
            window.removeFirst()
        }
        if (window.size < 2) return current()
        val span = window.last().timeMs - window.first().timeMs
        if (span < windowMs * 0.9) {
            // Window not full enough yet — don't update best.
            totalSpannedMs = maxOf(totalSpannedMs, span)
            return current()
        }
        totalSpannedMs = span
        var sum = 0.0
        for (s in window) sum += s.power
        val mean = sum / window.size
        if (!bestKnown || mean > bestMean) {
            bestMean = mean
            bestKnown = true
        }
        return current()
    }

    @Synchronized
    fun current(): Float? = if (bestKnown) bestMean.toFloat() else null

    @Synchronized
    fun reset() {
        window.clear()
        bestMean = 0.0
        bestKnown = false
        totalSpannedMs = 0L
    }
}
