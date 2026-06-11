package com.inqulab.heartkaroo.aet

/**
 * Within-ride aerobic-threshold estimator.
 *
 * Pairs the live DFA α1 stream against a short-window mean power and
 * solves for the power at which a linear fit of α1-vs-power crosses
 * `alphaTarget` (default 0.75 — the Rogell et al. 2021 marker for the
 * aerobic / first ventilatory threshold).
 *
 * Notes & guardrails:
 *  - Each α1 sample is paired with the average power over the last
 *    `powerSmoothingMs`. The default (2 min) matches the span of RR data
 *    a fresh α1 value actually reflects (its window needs ≥120 beats);
 *    pairing against a shorter power window mis-attributes the α1 of the
 *    previous minutes to the power of the last few seconds.
 *  - α1 samples outside [alphaMin, alphaMax] are dropped — far from the
 *    threshold the α1-vs-power relationship saturates and a linear fit
 *    becomes meaningless.
 *  - The fit slope must be negative (α decreases as power increases)
 *    for an estimate to be returned. Constant-intensity rides without
 *    enough power range will return null until they vary enough.
 *  - Returns null until at least `minSamples` paired samples accumulate.
 */
class AerobicThresholdCalibrator(
    private val alphaTarget: Double = 0.75,
    private val minSamples: Int = 60,
    private val powerSmoothingMs: Long = 120_000L,
    private val alphaMin: Double = 0.40,
    private val alphaMax: Double = 1.20,
) {
    private data class PowerSample(val timeMs: Long, val power: Double)
    private data class PairedSample(val alpha: Double, val power: Double)

    private val recentPowers = ArrayDeque<PowerSample>()
    private val paired = ArrayList<PairedSample>()

    @Synchronized
    fun addPower(timeMs: Long, power: Double) {
        if (power < 0.0 || !power.isFinite()) return
        recentPowers.addLast(PowerSample(timeMs, power))
        val cutoff = timeMs - powerSmoothingMs
        while (recentPowers.isNotEmpty() && recentPowers.first().timeMs < cutoff) {
            recentPowers.removeFirst()
        }
    }

    @Synchronized
    fun addAlpha(alpha: Float) {
        val a = alpha.toDouble()
        if (!a.isFinite() || a < alphaMin || a > alphaMax) return
        if (recentPowers.isEmpty()) return
        var sum = 0.0
        for (p in recentPowers) sum += p.power
        val avgP = sum / recentPowers.size
        if (avgP <= 0.0) return
        paired.add(PairedSample(a, avgP))
    }

    @Synchronized
    fun currentEstimate(): Float? = estimateForTarget(alphaTarget)

    /**
     * Power at which the α1-vs-power fit crosses [target]. Reuses the same
     * regression as [currentEstimate] so other thresholds (e.g. α1 = 0.50
     * for VT2 / the second ventilatory threshold) come for free.
     */
    @Synchronized
    fun estimateForTarget(target: Double): Float? {
        if (paired.size < minSamples) return null
        var sumX = 0.0; var sumY = 0.0; var sumXY = 0.0; var sumXX = 0.0
        for (s in paired) {
            sumX += s.power; sumY += s.alpha
            sumXY += s.power * s.alpha; sumXX += s.power * s.power
        }
        val n = paired.size.toDouble()
        val denom = n * sumXX - sumX * sumX
        if (denom == 0.0) return null
        val slope = (n * sumXY - sumX * sumY) / denom
        if (!slope.isFinite() || slope >= 0.0) return null
        val intercept = (sumY - slope * sumX) / n
        val power = (target - intercept) / slope
        if (!power.isFinite() || power <= 0.0) return null
        return power.toFloat()
    }

    val sampleCount: Int
        @Synchronized get() = paired.size

    @Synchronized
    fun reset() {
        recentPowers.clear()
        paired.clear()
    }
}
