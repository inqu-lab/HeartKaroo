package com.inqulab.heartkaroo.cadence

/**
 * Learns the cadence range at which you produce the most power per
 * heartbeat — your aerobic-efficiency sweet spot.
 *
 * Algorithm:
 *  1. Bin each (power, HR, cadence) sample into a fixed-width cadence
 *     bucket (default 10 RPM, range 50..120).
 *  2. For each bucket maintain running sums of power and HR.
 *  3. Efficiency per bucket = Σpower / ΣHR  (≡ avgPower / avgHR).
 *  4. The optimal cadence is the centre RPM of the bucket with the
 *     highest efficiency, provided at least `minSamplesPerBin` samples
 *     have landed there and at least `minTotalBinsWithData` buckets
 *     are populated (so we don't pick a singleton).
 *
 * Samples are filtered to plausible-physiology (power ≥ minPowerW,
 * HR > 0, cadence in range) so coasting and noise don't distort the
 * efficiency average.
 */
class OptimalCadenceCalculator(
    private val binWidthRpm: Int = 10,
    private val minRpm: Int = 50,
    private val maxRpm: Int = 120,
    private val minSamplesPerBin: Int = 30,
    private val minTotalBinsWithData: Int = 2,
    private val minPowerW: Double = 50.0,
) {
    private val nBins: Int = (maxRpm - minRpm) / binWidthRpm
    private val counts = IntArray(nBins)
    private val powerSums = DoubleArray(nBins)
    private val hrSums = DoubleArray(nBins)

    @Synchronized
    fun add(powerW: Double, hrBpm: Double, cadenceRpm: Double) {
        if (powerW < minPowerW || !powerW.isFinite()) return
        if (hrBpm <= 0.0 || !hrBpm.isFinite()) return
        if (cadenceRpm < minRpm || cadenceRpm >= maxRpm) return
        val bin = ((cadenceRpm - minRpm) / binWidthRpm).toInt().coerceIn(0, nBins - 1)
        counts[bin]++
        powerSums[bin] += powerW
        hrSums[bin] += hrBpm
    }

    /**
     * Returns the centre RPM of the most-efficient populated bin, or
     * null if there isn't enough data yet.
     */
    @Synchronized
    fun optimalCadence(): Float? {
        val populated = (0 until nBins).filter { counts[it] >= minSamplesPerBin }
        if (populated.size < minTotalBinsWithData) return null
        var bestBin = -1
        var bestEff = Double.NEGATIVE_INFINITY
        for (b in populated) {
            if (hrSums[b] <= 0.0) continue
            val eff = powerSums[b] / hrSums[b]
            if (eff > bestEff) { bestEff = eff; bestBin = b }
        }
        if (bestBin < 0) return null
        return (minRpm + bestBin * binWidthRpm + binWidthRpm / 2f)
    }

    /**
     * Aggregate sample count across all populated bins — a confidence
     * proxy used when persisting the per-ride final.
     */
    val totalSamples: Int
        @Synchronized get() = counts.sum()

    @Synchronized
    fun reset() {
        counts.fill(0)
        powerSums.fill(0.0)
        hrSums.fill(0.0)
    }
}
