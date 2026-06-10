package com.inqulab.heartkaroo.hrv

import kotlin.math.abs

/**
 * Real-time RR artifact rejection for DFA α1.
 *
 * DFA α1 is extremely sensitive to ectopic beats and missed/extra R-peak
 * detections — even a few percent of artifacts can swing the exponent enough
 * to move an aerobic-threshold estimate (Rogers et al., 2021; Gronwald et al.).
 * This is a pragmatic, streaming approximation of Kubios-style correction:
 * each incoming RR is compared against the median of the recently accepted
 * intervals, and any beat deviating by more than [thresholdFrac] is dropped so
 * it never enters the DFA window.
 *
 * A run of [maxConsecutiveRejections] rejections is treated as a genuine
 * baseline shift (e.g. a hard surge) rather than noise, so the reference
 * re-syncs instead of starving the DFA window.
 */
class RrArtifactCorrector(
    private val thresholdFrac: Double = 0.25,
    private val referenceSize: Int = 5,
    private val maxConsecutiveRejections: Int = 4,
    private val rateWindow: Int = 60,
) {
    private val recent = ArrayDeque<Int>()
    private var consecutiveRejections = 0
    private val decisions = ArrayDeque<Boolean>() // true = rejected as artifact

    /** Returns the RR to feed the DFA window, or null if it's an artifact. */
    // Synchronized like the sibling RR calculators: intervals arrive on the
    // RxJava computation thread while reset() runs from BLE callbacks and
    // disconnect paths on other threads.
    @Synchronized
    fun accept(rrMs: Int): Int? {
        if (recent.isEmpty()) {
            recent.addLast(rrMs)
            record(rejected = false)
            return rrMs
        }
        val ref = median(recent)
        val deviation = abs(rrMs - ref) / ref
        if (deviation > thresholdFrac) {
            consecutiveRejections++
            if (consecutiveRejections >= maxConsecutiveRejections) {
                // Sustained deviation: the baseline really moved, re-sync to it.
                recent.clear()
                recent.addLast(rrMs)
                consecutiveRejections = 0
                record(rejected = false)
                return rrMs
            }
            record(rejected = true)
            return null
        }
        consecutiveRejections = 0
        recent.addLast(rrMs)
        if (recent.size > referenceSize) recent.removeFirst()
        record(rejected = false)
        return rrMs
    }

    /** Fraction of recent beats rejected as artifacts (0..1) over [rateWindow].
     *  DFA α1 is unreliable once this is more than a few percent. */
    @Synchronized
    fun recentArtifactRate(): Double {
        if (decisions.isEmpty()) return 0.0
        return decisions.count { it }.toDouble() / decisions.size
    }

    @Synchronized
    fun reset() {
        recent.clear()
        consecutiveRejections = 0
        decisions.clear()
    }

    private fun record(rejected: Boolean) {
        decisions.addLast(rejected)
        if (decisions.size > rateWindow) decisions.removeFirst()
    }

    private fun median(values: Collection<Int>): Double {
        val sorted = values.sorted()
        val n = sorted.size
        return if (n % 2 == 1) sorted[n / 2].toDouble()
        else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
    }
}
