package com.inqulab.heartkaroo.hrv

/** DFA α1 values that mark the two lactate thresholds (Rogell et al., 2021).
 *  Shared so the live in-ride colouring and [DfaAlphaZoneTimer] agree on the
 *  band edges. */
const val DFA_LT1_ALPHA = 0.75
const val DFA_LT2_ALPHA = 0.50

/**
 * Which side of LT1 / LT2 a live DFA α1 value sits on, with the background
 * colour and label used to surface it on the in-ride data field.
 */
enum class DfaZone(val label: String, val color: Int) {
    BELOW_LT1("BELOW LT1", 0xFF1E88E5.toInt()), // easy aerobic — blue
    LT1_TO_LT2("LT1–LT2", 0xFFFB8C00.toInt()), // tempo / threshold — amber
    ABOVE_LT2("ABOVE LT2", 0xFFE53935.toInt()), // hard — red
}

fun classifyDfaZone(alpha: Double): DfaZone = when {
    alpha >= DFA_LT1_ALPHA -> DfaZone.BELOW_LT1
    alpha >= DFA_LT2_ALPHA -> DfaZone.LT1_TO_LT2
    else -> DfaZone.ABOVE_LT2
}
