package com.inqulab.heartkaroo.wprime

import com.inqulab.heartkaroo.karoo.Zone
import com.inqulab.heartkaroo.karoo.ZoneColors

/** W′ balance as an anaerobic "fuel gauge", coloured by the share of W′₀ left:
 *  ≥ 66 % fresh, 33–66 % working, < 33 % low. [maxJ] is the rider's W′₀; a
 *  non-positive max can't form a ratio, so it reads neutral. */
fun wPrimeZone(balanceJ: Float, maxJ: Float): Zone {
    if (maxJ <= 0f) return Zone("W′", ZoneColors.NEUTRAL)
    val pct = (balanceJ / maxJ * 100f).coerceIn(0f, 100f)
    return when {
        pct >= 66f -> Zone("FRESH", ZoneColors.GREEN)
        pct >= 33f -> Zone("WORKING", ZoneColors.AMBER)
        else -> Zone("LOW", ZoneColors.RED)
    }
}
