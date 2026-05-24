package com.inqulab.heartkaroo.karoo

/** A colour band for a live metric: the background [color] and a short [label]
 *  shown on a graphical data field. Pure (ARGB Int) so classifiers stay
 *  JVM-testable without the Android framework. */
data class Zone(val label: String, val color: Int)

/** Shared palette so every coloured field reads consistently. White text is
 *  legible on all of these. */
object ZoneColors {
    const val BLUE = 0xFF1E88E5.toInt() // easy / low intensity
    const val GREEN = 0xFF2E7D32.toInt() // good / well-coupled / fresh
    const val AMBER = 0xFFFB8C00.toInt() // moderate
    const val ORANGE = 0xFFEF6C00.toInt() // hard
    const val RED = 0xFFE53935.toInt() // very hard / depleted
    const val NEUTRAL = 0xFF424242.toInt() // no value yet / warming up
}
