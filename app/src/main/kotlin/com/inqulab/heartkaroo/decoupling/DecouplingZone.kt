package com.inqulab.heartkaroo.decoupling

import com.inqulab.heartkaroo.karoo.Zone
import com.inqulab.heartkaroo.karoo.ZoneColors

/** Friel decoupling bands: < 5 % is well-coupled aerobic effort, 5–10 % is
 *  drifting, > 10 % is decoupled (cardiac drift / fatigue). Applies to both
 *  Pw:Hr and Pa:Hr decoupling. */
fun decouplingZone(percent: Float): Zone = when {
    percent < 5f -> Zone("COUPLED", ZoneColors.GREEN)
    percent < 10f -> Zone("DRIFT", ZoneColors.AMBER)
    else -> Zone("DECOUPLED", ZoneColors.RED)
}
