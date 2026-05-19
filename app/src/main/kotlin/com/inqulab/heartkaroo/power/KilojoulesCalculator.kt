package com.inqulab.heartkaroo.power

/**
 * Integrates power over time to report cumulative work done in kJ.
 *
 * kJ ≈ kcal at typical cycling efficiency (~24 %), since the metabolic
 * cost is roughly 4× the mechanical work and 1 kcal = 4.184 kJ — the
 * factors cancel within ~5 %. Most head units report the same number
 * as both "kJ" and "Calories".
 */
class KilojoulesCalculator {
    private var lastTimeMs: Long = -1L
    private var lastPower: Double = 0.0
    private var totalJ: Double = 0.0

    @Synchronized
    fun add(timeMs: Long, power: Double): Float {
        if (power < 0.0 || !power.isFinite()) return (totalJ / 1000.0).toFloat()
        if (lastTimeMs >= 0L && timeMs > lastTimeMs) {
            val dt = (timeMs - lastTimeMs) / 1000.0
            // Trapezoidal: J = average power × dt
            totalJ += 0.5 * (lastPower + power) * dt
        }
        lastTimeMs = timeMs
        lastPower = power
        return (totalJ / 1000.0).toFloat()
    }

    @Synchronized
    fun current(): Float = (totalJ / 1000.0).toFloat()

    @Synchronized
    fun reset() {
        lastTimeMs = -1L
        lastPower = 0.0
        totalJ = 0.0
    }
}
