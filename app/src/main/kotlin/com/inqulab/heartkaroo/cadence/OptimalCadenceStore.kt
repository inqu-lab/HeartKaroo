package com.inqulab.heartkaroo.cadence

import android.content.Context

/**
 * Rolling history of per-ride optimal-cadence estimates in
 * SharedPreferences. Same shape as AerobicThresholdStore: 90-day window,
 * sample-count-weighted mean as the long-term estimate.
 */
class OptimalCadenceStore(context: Context) {

    private val prefs = context.getSharedPreferences("optimal_cadence", Context.MODE_PRIVATE)

    data class Entry(val timeMs: Long, val cadenceRpm: Float, val sampleCount: Int)

    fun record(timeMs: Long, cadenceRpm: Float, sampleCount: Int) {
        if (cadenceRpm <= 0f || sampleCount <= 0) return
        val kept = entries(timeMs).toMutableList()
        kept.add(Entry(timeMs, cadenceRpm, sampleCount))
        prefs.edit().putString(KEY, encode(kept)).apply()
    }

    fun entries(nowMs: Long = System.currentTimeMillis()): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val cutoff = nowMs - WINDOW_MS
        return raw.split(",").mapNotNull { decode(it) }.filter { it.timeMs >= cutoff }
    }

    fun rollingEstimate(nowMs: Long = System.currentTimeMillis()): Float? =
        weightedMean(entries(nowMs))

    fun clear() { prefs.edit().remove(KEY).apply() }

    companion object {
        private const val KEY = "ride_optimal_cadences"
        private const val WINDOW_MS = 90L * 24 * 60 * 60 * 1000

        fun weightedMean(entries: List<Entry>): Float? {
            val good = entries.filter { it.sampleCount > 0 && it.cadenceRpm > 0f }
            if (good.isEmpty()) return null
            val totalWeight = good.sumOf { it.sampleCount.toLong() }
            if (totalWeight <= 0L) return null
            val weighted = good.sumOf { it.cadenceRpm.toDouble() * it.sampleCount } / totalWeight
            return weighted.toFloat()
        }

        private fun encode(es: List<Entry>): String =
            es.joinToString(",") { "${it.timeMs}:${it.cadenceRpm}:${it.sampleCount}" }

        private fun decode(raw: String): Entry? {
            val parts = raw.split(":")
            if (parts.size != 3) return null
            val t = parts[0].toLongOrNull() ?: return null
            val r = parts[1].toFloatOrNull() ?: return null
            val c = parts[2].toIntOrNull() ?: return null
            return Entry(t, r, c)
        }
    }
}
