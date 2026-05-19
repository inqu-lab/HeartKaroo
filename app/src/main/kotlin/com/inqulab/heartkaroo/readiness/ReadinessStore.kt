package com.inqulab.heartkaroo.readiness

import android.content.Context
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Rolling 7-day baseline of pre-ride RMSSD readings.
 *
 * Uses log-RMSSD (lnRMSSD) which is the form recommended for daily HRV
 * tracking — closer to normally distributed than raw RMSSD, so a simple
 * mean ± SD baseline is meaningful.
 *
 * Stored in SharedPreferences as a comma-separated list of "epochMs:rmssd"
 * entries; ancient ones (>7 days) are dropped on read.
 */
class ReadinessStore(context: Context) {

    private val prefs = context.getSharedPreferences("readiness", Context.MODE_PRIVATE)

    data class Reading(val timeMs: Long, val rmssdMs: Float)

    fun record(timeMs: Long, rmssdMs: Float) {
        val kept = recent(timeMs).toMutableList()
        kept.add(Reading(timeMs, rmssdMs))
        prefs.edit().putString(KEY, kept.joinToString(",") { "${it.timeMs}:${it.rmssdMs}" }).apply()
    }

    fun recent(nowMs: Long = System.currentTimeMillis()): List<Reading> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val cutoff = nowMs - WINDOW_MS
        return raw.split(",").mapNotNull { entry ->
            val parts = entry.split(":")
            if (parts.size != 2) return@mapNotNull null
            val t = parts[0].toLongOrNull() ?: return@mapNotNull null
            val v = parts[1].toFloatOrNull() ?: return@mapNotNull null
            if (t < cutoff) null else Reading(t, v)
        }
    }

    data class Status(
        val todayRmssd: Float,
        val baselineLnMean: Float,
        val baselineLnSd: Float,
        val zScore: Float,
        val label: Verdict,
    )

    enum class Verdict { GO_HARD, GO_EASY, NORMAL, NO_BASELINE }

    fun statusFor(todayRmssd: Float, nowMs: Long = System.currentTimeMillis()): Status {
        val baseline = recent(nowMs).filter { it.rmssdMs > 0f }.map { ln(it.rmssdMs.toDouble()) }
        if (baseline.size < 3) {
            return Status(
                todayRmssd = todayRmssd,
                baselineLnMean = 0f,
                baselineLnSd = 0f,
                zScore = 0f,
                label = Verdict.NO_BASELINE,
            )
        }
        val mean = baseline.average()
        val variance = baseline.sumOf { (it - mean) * (it - mean) } / (baseline.size - 1)
        val sd = sqrt(variance)
        val z = if (sd > 0.0) (ln(todayRmssd.toDouble()) - mean) / sd else 0.0
        val verdict = when {
            z >= 0.5 -> Verdict.GO_HARD
            z <= -0.5 -> Verdict.GO_EASY
            else -> Verdict.NORMAL
        }
        return Status(
            todayRmssd = todayRmssd,
            baselineLnMean = mean.toFloat(),
            baselineLnSd = sd.toFloat(),
            zScore = z.toFloat(),
            label = verdict,
        )
    }

    private companion object {
        const val KEY = "rmssd_readings"
        const val WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    }
}
