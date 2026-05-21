package com.inqulab.heartkaroo

/**
 * Decides when to raise the low-strap-battery in-ride alert. Fires once
 * when the level first drops to [warnAtPct] or below, and re-arms only
 * after it recovers above [rearmAbovePct] (a fresh battery) — so a level
 * hovering around the threshold doesn't alert repeatedly.
 */
internal class StrapBatteryAlerter(
    private val warnAtPct: Int = 15,
    private val rearmAbovePct: Int = 25,
) {
    private var warned = false

    /** True exactly on the transition that should raise an alert. */
    fun shouldAlert(level: Int): Boolean {
        if (level <= warnAtPct && !warned) {
            warned = true
            return true
        }
        if (level > rearmAbovePct) warned = false
        return false
    }
}

/**
 * Whether a per-ride rolling metric (AeT estimate, optimal cadence) should
 * be persisted on ride stop: only when an estimate exists and it was backed
 * by at least [minSamples] samples, so a too-short or data-starved ride
 * doesn't pollute the rolling baseline.
 */
internal fun shouldPersistRollingFinal(estimate: Float?, samples: Int, minSamples: Int): Boolean =
    estimate != null && samples >= minSamples
