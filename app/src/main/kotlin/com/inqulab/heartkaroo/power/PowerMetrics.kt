package com.inqulab.heartkaroo.power

/**
 * Pure power-metric formulas shared by the Variability Index, Intensity
 * Factor and TSS data fields. Kept out of the DataTypes so the math is
 * unit-testable without the Karoo streaming stack.
 */
object PowerMetrics {

    /** Variability Index = Normalized Power / Average Power. */
    fun variabilityIndex(normalizedPower: Float?, averagePower: Float?): Float? =
        if (normalizedPower != null && averagePower != null && averagePower > 0f) {
            normalizedPower / averagePower
        } else {
            null
        }

    /** Intensity Factor = Normalized Power / FTP (FTP floored at 1 W). */
    fun intensityFactor(normalizedPower: Float?, ftpW: Int): Float? =
        normalizedPower?.let { it / ftpW.coerceAtLeast(1) }

    /**
     * Coggan Training Stress Score = duration_hours x IF^2 x 100.
     * Cumulative over the ride; grows with both intensity and time.
     */
    fun trainingStressScore(normalizedPower: Float?, ftpW: Int, elapsedSec: Double): Float? {
        if (normalizedPower == null) return null
        val intensityFactor = normalizedPower / ftpW.coerceAtLeast(1)
        return (elapsedSec / 3600.0 * intensityFactor * intensityFactor * 100.0).toFloat()
    }

    /** Live FTP estimate = 95 % of the best 20-minute power (the classic
     *  20-min field-test protocol). */
    fun eftp(best20MinPower: Float?): Float? = best20MinPower?.times(0.95f)

    /** W′ balance as a percentage of W′₀ (floored at 1 J), clamped to 0–100
     *  so it reads as a fuel gauge even when the model is overdrawn. */
    fun wPrimePercent(balanceJ: Float?, wPrimeJ: Int): Float? =
        balanceJ?.let { (it / wPrimeJ.coerceAtLeast(1) * 100f).coerceIn(0f, 100f) }
}
