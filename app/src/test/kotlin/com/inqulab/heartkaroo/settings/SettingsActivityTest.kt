package com.inqulab.heartkaroo.settings

import android.content.Context
import android.widget.Button
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.inqulab.heartkaroo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for SettingsActivity — inflates the real layout, drives
 * the Save/Reset buttons, and checks the store + finish() behaviour. Covers
 * the range-validation logic that the pure RiderSettings test can't reach.
 */
@RunWith(RobolectricTestRunner::class)
class SettingsActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("rider", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    private fun launch() = Robolectric.buildActivity(SettingsActivity::class.java).setup().get()

    @Test
    fun `fields are populated from the store defaults on launch`() {
        val a = launch()
        assertEquals(
            RiderSettings.DEFAULT_FTP.toString(),
            a.findViewById<EditText>(R.id.settings_ftp).text.toString(),
        )
        assertEquals(
            RiderSettings.DEFAULT_WEIGHT.toString(),
            a.findViewById<EditText>(R.id.settings_weight).text.toString(),
        )
    }

    @Test
    fun `saving valid input persists every field and finishes`() {
        val a = launch()
        a.findViewById<EditText>(R.id.settings_ftp).setText("300")
        a.findViewById<EditText>(R.id.settings_cp).setText("280")
        a.findViewById<EditText>(R.id.settings_wprime).setText("22500")
        a.findViewById<EditText>(R.id.settings_hrmax).setText("185")
        a.findViewById<EditText>(R.id.settings_weight).setText("68.5")

        a.findViewById<Button>(R.id.settings_save).performClick()

        val saved = RiderSettings(context)
        assertEquals(300, saved.ftpW)
        assertEquals(280, saved.criticalPowerW)
        assertEquals(22_500, saved.wPrimeJ)
        assertEquals(185, saved.hrMax)
        assertEquals(68.5f, saved.weightKg, 1e-4f)
        assertTrue("activity should finish after a successful save", a.isFinishing)
    }

    @Test
    fun `out-of-range input cancels the save and keeps the activity open`() {
        val a = launch()
        a.findViewById<EditText>(R.id.settings_ftp).setText("9999") // above the 600 cap

        a.findViewById<Button>(R.id.settings_save).performClick()

        // Nothing persisted; the getter still returns the default.
        assertEquals(RiderSettings.DEFAULT_FTP, RiderSettings(context).ftpW)
        assertFalse("activity should stay open on invalid input", a.isFinishing)
    }

    @Test
    fun `reset restores defaults in the fields without persisting`() {
        RiderSettings(context).ftpW = 333
        val a = launch()
        assertEquals("333", a.findViewById<EditText>(R.id.settings_ftp).text.toString())

        a.findViewById<Button>(R.id.settings_reset).performClick()

        assertEquals(
            RiderSettings.DEFAULT_FTP.toString(),
            a.findViewById<EditText>(R.id.settings_ftp).text.toString(),
        )
        // Reset is in-memory only — the store keeps the old value until Save.
        assertEquals(333, RiderSettings(context).ftpW)
    }
}
