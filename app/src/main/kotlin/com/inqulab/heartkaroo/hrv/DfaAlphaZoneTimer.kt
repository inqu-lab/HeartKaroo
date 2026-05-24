package com.inqulab.heartkaroo.hrv

/**
 * Accumulates ride time spent in DFA α1 intensity zones — an HRV-based
 * intensity distribution that downstream tools can't derive without the
 * raw R-R stream.
 *
 *  - aerobic:   α1 ≥ 0.75          (below the first threshold, LT1)
 *  - threshold: 0.50 ≤ α1 < 0.75   (between LT1 and LT2)
 *  - hard:      α1 < 0.50          (above the second threshold, LT2)
 *
 * Each new sample attributes the elapsed time since the previous sample
 * to the band the previous α1 value sat in. Gaps longer than [maxGapMs]
 * (e.g. a strap dropout or a pause) are not attributed to any band.
 */
class DfaAlphaZoneTimer(
    private val aerobicMin: Double = DFA_LT1_ALPHA,
    private val thresholdMin: Double = DFA_LT2_ALPHA,
    private val maxGapMs: Long = 10_000L,
) {
    private var lastTimeMs: Long = -1L
    private var lastAlpha: Double = Double.NaN
    private var aerobicMs: Long = 0L
    private var thresholdMs: Long = 0L
    private var hardMs: Long = 0L

    @Synchronized
    fun add(timeMs: Long, alpha: Double) {
        if (lastTimeMs >= 0L && !lastAlpha.isNaN()) {
            val dt = timeMs - lastTimeMs
            if (dt in 0..maxGapMs) {
                when {
                    lastAlpha >= aerobicMin -> aerobicMs += dt
                    lastAlpha >= thresholdMin -> thresholdMs += dt
                    else -> hardMs += dt
                }
            }
        }
        lastTimeMs = timeMs
        lastAlpha = alpha
    }

    @Synchronized fun aerobicSeconds(): Double = aerobicMs / 1000.0
    @Synchronized fun thresholdSeconds(): Double = thresholdMs / 1000.0
    @Synchronized fun hardSeconds(): Double = hardMs / 1000.0

    @Synchronized
    fun reset() {
        lastTimeMs = -1L
        lastAlpha = Double.NaN
        aerobicMs = 0L
        thresholdMs = 0L
        hardMs = 0L
    }
}
