package com.inqulab.heartkaroo.hrv

import kotlin.math.exp

/**
 * Tracks an EMA baseline of RMSSD and reports current RMSSD as a stress %
 * relative to that baseline.
 *
 * stress% = clamp( (baseline - rmssd) / baseline * 100, 0, 100 )
 *
 * The baseline uses a time-based EMA: alpha = 1 - exp(-dt / tau), so updates
 * remain correct even if the source flow stutters (BLE dropouts, irregular
 * notifications). Results are gated until at least `warmupMs` of samples have
 * been observed, matching the warmup pattern used by DecouplingCalculator.
 */
class HRVStressCalculator(
    private val tauMs: Long = 20 * 60 * 1000L,
    private val warmupMs: Long = 5 * 60 * 1000L,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    private var baseline: Float = 0f
    private var lastRmssd: Float = 0f
    private var firstSampleTimeMs: Long = -1L
    private var lastUpdateTimeMs: Long = -1L

    @Synchronized
    fun addRmssd(rmssd: Float) {
        if (rmssd <= 0f) return
        val now = clockMs()
        if (firstSampleTimeMs < 0L) {
            firstSampleTimeMs = now
            baseline = rmssd
        } else {
            val dt = (now - lastUpdateTimeMs).coerceAtLeast(0L)
            val alpha = 1.0 - exp(-dt.toDouble() / tauMs.toDouble())
            baseline = (baseline + alpha * (rmssd - baseline)).toFloat()
        }
        lastRmssd = rmssd
        lastUpdateTimeMs = now
    }

    @Synchronized
    fun getStressPct(): Float? {
        if (firstSampleTimeMs < 0L) return null
        if (clockMs() - firstSampleTimeMs < warmupMs) return null
        if (baseline <= 0f) return null
        val raw = (baseline - lastRmssd) / baseline * 100f
        return raw.coerceIn(0f, 100f)
    }

    @Synchronized
    fun reset() {
        baseline = 0f
        lastRmssd = 0f
        firstSampleTimeMs = -1L
        lastUpdateTimeMs = -1L
    }
}
