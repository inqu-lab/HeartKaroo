package com.inqulab.heartkaroo.wprime

import kotlin.math.exp

/**
 * Skiba-style W′ balance — the "matches left in the box" model of
 * anaerobic capacity.
 *
 * When power > CP the rider is burning W′:
 *   W′_bal(t+dt) = W′_bal(t) - (P - CP) * dt
 *
 * When power < CP the rider is recovering:
 *   W′_bal(t+dt) = W′_bal(t) + (W′₀ - W′_bal(t)) * (1 - exp(-dt/τ))
 *
 * with τ defined dynamically (Skiba 2012):
 *   τ = 546 * exp(-0.01 * D_CP) + 316
 * where D_CP = CP - mean power during the most recent below-CP segment.
 *
 * CP and W′₀ default to broadly typical values for an amateur road
 * cyclist and can be overridden by the caller. A real UI for these
 * settings is intentionally deferred — defaults are good enough to make
 * the field directionally useful and a settings screen can land later.
 */
class WPrimeBalanceCalculator(
    private val criticalPowerW: Double = 250.0,
    private val wPrimeJ: Double = 20_000.0,
) {
    private var balance: Double = wPrimeJ
    private var lastTimeMs: Long = -1L

    // Track below-CP samples to estimate τ
    private var belowSum: Double = 0.0
    private var belowCount: Int = 0
    private var lastSegmentAboveCp: Boolean = false

    @Synchronized
    fun add(timeMs: Long, powerW: Double): Float {
        if (lastTimeMs < 0L) {
            lastTimeMs = timeMs
            return balance.toFloat()
        }
        val dt = (timeMs - lastTimeMs).coerceAtLeast(0L) / 1000.0
        lastTimeMs = timeMs
        if (dt <= 0.0) return balance.toFloat()

        if (powerW > criticalPowerW) {
            // Reset below-CP accumulator when we cross above CP
            if (!lastSegmentAboveCp) {
                belowSum = 0.0
                belowCount = 0
            }
            lastSegmentAboveCp = true
            balance -= (powerW - criticalPowerW) * dt
            if (balance < 0.0) balance = 0.0
        } else {
            lastSegmentAboveCp = false
            belowSum += powerW
            belowCount++
            val belowMean = if (belowCount > 0) belowSum / belowCount else 0.0
            val dCp = (criticalPowerW - belowMean).coerceAtLeast(1.0)
            val tau = 546.0 * exp(-0.01 * dCp) + 316.0
            val alpha = 1.0 - exp(-dt / tau)
            balance += (wPrimeJ - balance) * alpha
            if (balance > wPrimeJ) balance = wPrimeJ
        }
        return balance.toFloat()
    }

    @Synchronized
    fun current(): Float = balance.toFloat()

    @Synchronized
    fun reset() {
        balance = wPrimeJ
        lastTimeMs = -1L
        belowSum = 0.0
        belowCount = 0
        lastSegmentAboveCp = false
    }
}
