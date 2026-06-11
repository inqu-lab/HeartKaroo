package com.inqulab.heartkaroo.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device round-trip of RiderSettings against real SharedPreferences —
 * notably the paired-strap MAC, which decides WHICH strap every connect path
 * (extension service, Readiness screen) goes after. A persistence bug here
 * would silently re-pair the rider to the wrong strap.
 */
@RunWith(AndroidJUnit4::class)
class RiderSettingsInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("rider", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun pairedStrapMacRoundTripsAndClears() {
        val settings = RiderSettings(context)
        assertNull(settings.pairedStrapMac)

        settings.pairedStrapMac = "AA:BB:CC:DD:EE:FF"
        // A fresh instance must read the persisted value, not cached state.
        assertEquals("AA:BB:CC:DD:EE:FF", RiderSettings(context).pairedStrapMac)

        settings.pairedStrapMac = null
        assertNull(RiderSettings(context).pairedStrapMac)
    }

    @Test
    fun riderNumbersPersistAcrossInstances() {
        val settings = RiderSettings(context)
        settings.ftpW = 305
        settings.criticalPowerW = 280
        settings.wPrimeJ = 21_500
        settings.hrMax = 187
        settings.weightKg = 71.5f

        val reread = RiderSettings(context)
        assertEquals(305, reread.ftpW)
        assertEquals(280, reread.criticalPowerW)
        assertEquals(21_500, reread.wPrimeJ)
        assertEquals(187, reread.hrMax)
        assertEquals(71.5f, reread.weightKg, 1e-4f)
    }
}
