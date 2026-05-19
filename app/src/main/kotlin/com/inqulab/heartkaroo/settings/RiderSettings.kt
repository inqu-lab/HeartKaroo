package com.inqulab.heartkaroo.settings

import android.content.Context

/**
 * SharedPreferences-backed rider settings — FTP (W), critical power (W),
 * W′₀ (J), HRmax, weight. Defaults are sensible for an amateur road
 * cyclist; a future Settings screen will let the user override them.
 *
 * Read via the per-field getters; write via the corresponding setters.
 * Multiple data fields can share the same store; SharedPreferences is
 * process-wide and thread-safe for simple reads/writes.
 */
class RiderSettings(context: Context) {
    private val prefs = context.getSharedPreferences("rider", Context.MODE_PRIVATE)

    var ftpW: Int
        get() = prefs.getInt(KEY_FTP, DEFAULT_FTP)
        set(v) { prefs.edit().putInt(KEY_FTP, v).apply() }

    var criticalPowerW: Int
        get() = prefs.getInt(KEY_CP, DEFAULT_CP)
        set(v) { prefs.edit().putInt(KEY_CP, v).apply() }

    var wPrimeJ: Int
        get() = prefs.getInt(KEY_WPRIME, DEFAULT_WPRIME)
        set(v) { prefs.edit().putInt(KEY_WPRIME, v).apply() }

    var hrMax: Int
        get() = prefs.getInt(KEY_HRMAX, DEFAULT_HRMAX)
        set(v) { prefs.edit().putInt(KEY_HRMAX, v).apply() }

    var weightKg: Float
        get() = prefs.getFloat(KEY_WEIGHT, DEFAULT_WEIGHT)
        set(v) { prefs.edit().putFloat(KEY_WEIGHT, v).apply() }

    companion object {
        const val DEFAULT_FTP = 270
        const val DEFAULT_CP = 250
        const val DEFAULT_WPRIME = 20_000
        const val DEFAULT_HRMAX = 190
        const val DEFAULT_WEIGHT = 75f

        private const val KEY_FTP = "ftp_w"
        private const val KEY_CP = "cp_w"
        private const val KEY_WPRIME = "wprime_j"
        private const val KEY_HRMAX = "hr_max"
        private const val KEY_WEIGHT = "weight_kg"
    }
}
