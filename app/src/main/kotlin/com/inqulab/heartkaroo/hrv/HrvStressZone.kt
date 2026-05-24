package com.inqulab.heartkaroo.hrv

import com.inqulab.heartkaroo.karoo.Zone
import com.inqulab.heartkaroo.karoo.ZoneColors

/** HRV stress % (current RMSSD vs in-ride baseline, 0–100): lower is calmer.
 *  < 33 low, 33–66 moderate, ≥ 66 high. */
fun hrvStressZone(stressPercent: Float): Zone = when {
    stressPercent < 33f -> Zone("LOW", ZoneColors.GREEN)
    stressPercent < 66f -> Zone("MODERATE", ZoneColors.AMBER)
    else -> Zone("HIGH", ZoneColors.RED)
}
