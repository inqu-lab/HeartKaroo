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
}
