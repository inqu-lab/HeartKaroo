package com.inqulab.heartkaroo.power

import android.content.Context

/**
 * Rolling history of per-ride eFTP finals (95 % of best 20-min power)
 * persisted across app sessions in SharedPreferences.
 *
 * Entries older than 42 days are dropped on read. Unlike the AeT store's
 * weighted mean, the rolling estimate here is the window MAX: an easy ride
 * says nothing about FTP (its best 20 min is just easy riding), so only
 * the strongest effort in the window is informative. 42 days is the
 * common eFTP decay horizon.
 */
class EftpStore(context: Context) {

    private val prefs = context.getSharedPreferences("eftp", Context.MODE_PRIVATE)

    data class Entry(val timeMs: Long, val eftpW: Float)

    fun record(timeMs: Long, eftpW: Float) {
        if (eftpW <= 0f) return
        val kept = entries(timeMs).toMutableList()
        kept.add(Entry(timeMs, eftpW))
        prefs.edit().putString(KEY, encode(kept)).apply()
    }

    fun entries(nowMs: Long = System.currentTimeMillis()): List<Entry> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val cutoff = nowMs - WINDOW_MS
        return raw.split(",").mapNotNull { decode(it) }.filter { it.timeMs >= cutoff }
    }

    fun rollingBest(nowMs: Long = System.currentTimeMillis()): Float? =
        entries(nowMs).maxOfOrNull { it.eftpW }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    companion object {
        private const val KEY = "ride_finals"
        private const val WINDOW_MS = 42L * 24 * 60 * 60 * 1000

        private fun encode(es: List<Entry>): String =
            es.joinToString(",") { "${it.timeMs}:${it.eftpW}" }

        private fun decode(raw: String): Entry? {
            val parts = raw.split(":")
            if (parts.size != 2) return null
            val t = parts[0].toLongOrNull() ?: return null
            val w = parts[1].toFloatOrNull() ?: return null
            return Entry(t, w)
        }
    }
}
