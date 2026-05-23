package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.karoo.Zone
import com.inqulab.heartkaroo.karoo.ZoneColors

/** Coggan Intensity Factor (NP / FTP) training bands:
 *  < 0.75 recovery, 0.75–0.85 endurance, 0.85–0.95 tempo,
 *  0.95–1.05 threshold, ≥ 1.05 VO2 max and above. */
fun intensityFactorZone(intensityFactor: Float): Zone = when {
    intensityFactor < 0.75f -> Zone("RECOVERY", ZoneColors.BLUE)
    intensityFactor < 0.85f -> Zone("ENDURANCE", ZoneColors.GREEN)
    intensityFactor < 0.95f -> Zone("TEMPO", ZoneColors.AMBER)
    intensityFactor < 1.05f -> Zone("THRESHOLD", ZoneColors.ORANGE)
    else -> Zone("VO2+", ZoneColors.RED)
}
