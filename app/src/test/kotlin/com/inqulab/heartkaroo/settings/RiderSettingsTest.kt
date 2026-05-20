package com.inqulab.heartkaroo.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for the SharedPreferences-backed RiderSettings:
 * defaults, getter/setter round-trips, and persistence across instances.
 */
@RunWith(RobolectricTestRunner::class)
class RiderSettingsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun freshSettings(): RiderSettings {
        context.getSharedPreferences("rider", Context.MODE_PRIVATE)
            .edit().clear().commit()
        return RiderSettings(context)
    }

    @Test
    fun `unset fields return the documented defaults`() {
        val s = freshSettings()
        assertEquals(RiderSettings.DEFAULT_FTP, s.ftpW)
        assertEquals(RiderSettings.DEFAULT_CP, s.criticalPowerW)
        assertEquals(RiderSettings.DEFAULT_WPRIME, s.wPrimeJ)
        assertEquals(RiderSettings.DEFAULT_HRMAX, s.hrMax)
        assertEquals(RiderSettings.DEFAULT_WEIGHT, s.weightKg, 1e-4f)
    }

    @Test
    fun `each field round-trips through its setter and getter`() {
        val s = freshSettings()
        s.ftpW = 300
        s.criticalPowerW = 280
        s.wPrimeJ = 22_500
        s.hrMax = 185
        s.weightKg = 68.5f

        assertEquals(300, s.ftpW)
        assertEquals(280, s.criticalPowerW)
        assertEquals(22_500, s.wPrimeJ)
        assertEquals(185, s.hrMax)
        assertEquals(68.5f, s.weightKg, 1e-4f)
    }

    @Test
    fun `written values persist for a new instance over the same prefs`() {
        freshSettings().apply {
            ftpW = 312
            weightKg = 71.2f
        }

        val reloaded = RiderSettings(context)
        assertEquals(312, reloaded.ftpW)
        assertEquals(71.2f, reloaded.weightKg, 1e-4f)
        // Untouched fields still fall back to defaults.
        assertEquals(RiderSettings.DEFAULT_HRMAX, reloaded.hrMax)
    }
}
