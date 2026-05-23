package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.karoo.Zone
import com.inqulab.heartkaroo.karoo.ZoneColors
import kotlin.math.roundToInt

/** Coggan quadrant (1–4) as a categorical colour:
 *  Q1 high-force/high-cadence (threshold), Q2 high-force/low-cadence (grind),
 *  Q3 low-force/low-cadence (easy), Q4 low-force/high-cadence (spin). */
fun quadrantZone(quadrant: Float): Zone = when (quadrant.roundToInt()) {
    1 -> Zone("THRESHOLD", ZoneColors.RED)
    2 -> Zone("GRIND", ZoneColors.ORANGE)
    3 -> Zone("EASY", ZoneColors.GREEN)
    4 -> Zone("SPIN", ZoneColors.BLUE)
    else -> Zone("QUAD", ZoneColors.NEUTRAL)
}
