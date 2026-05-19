package com.inqulab.heartkaroo.aet

import android.content.Context

/**
 * Rolling history of per-ride AeT estimates persisted across app
 * sessions in SharedPreferences.
 *
 * Entries older than 90 days are dropped on read. The rolling estimate
 * is a sample-count-weighted mean — longer rides with more α1 samples
 * carry more weight than a 20-minute warm-up that happened to scrape
 * past the calibrator's minimum-sample threshold.
 */
class AerobicThresholdStore(context: Context) {

    private val prefs = context.getSharedPreferences("aet", Context.MODE_PRIVATE)

    data class Entry(val timeMs: Long, val aetW: Float, val sampleCount: Int)

    fun record(timeMs: Long, aetW: Float, sampleCount: Int) {
        if (aetW <= 0f || sampleCount <= 0) return
        val kept = entries(timeMs).toMutableList()
        kept.add(Entry(timeMs, aetW, sampleCount))
        prefs.edit().putString(KEY, encode(kept)).apply()
    }

    fun entries(nowMs: Long = System.currentTimeMillis()): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val cutoff = nowMs - WINDOW_MS
        return raw.split(",").mapNotNull { decode(it) }.filter { it.timeMs >= cutoff }
    }

    fun rollingEstimate(nowMs: Long = System.currentTimeMillis()): Float? =
        weightedMean(entries(nowMs))

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "ride_estimates"
        private const val WINDOW_MS = 90L * 24 * 60 * 60 * 1000

        fun weightedMean(entries: List<Entry>): Float? {
            val good = entries.filter { it.sampleCount > 0 && it.aetW > 0f }
            if (good.isEmpty()) return null
            val totalWeight = good.sumOf { it.sampleCount.toLong() }
            if (totalWeight <= 0L) return null
            val weighted = good.sumOf { it.aetW.toDouble() * it.sampleCount } / totalWeight
            return weighted.toFloat()
        }

        private fun encode(es: List<Entry>): String =
            es.joinToString(",") { "${it.timeMs}:${it.aetW}:${it.sampleCount}" }

        private fun decode(raw: String): Entry? {
            val parts = raw.split(":")
            if (parts.size != 3) return null
            val t = parts[0].toLongOrNull() ?: return null
            val w = parts[1].toFloatOrNull() ?: return null
            val c = parts[2].toIntOrNull() ?: return null
            return Entry(t, w, c)
        }
    }
}
