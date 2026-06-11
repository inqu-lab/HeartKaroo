package com.inqulab.heartkaroo.readiness

import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.inqulab.heartkaroo.R
import com.inqulab.heartkaroo.aet.AerobicThresholdStore
import com.inqulab.heartkaroo.cadence.OptimalCadenceStore
import com.inqulab.heartkaroo.power.EftpStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric tests for ReadinessActivity's on-launch rendering: the
 * baseline / AeT / optimal-cadence summaries in both their empty and
 * populated states, plus the permission-gathering path. The 2-minute BLE
 * measurement loop needs a real strap and isn't exercised here.
 */
@RunWith(RobolectricTestRunner::class)
class ReadinessActivityTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPrefs() {
        for (name in listOf("readiness", "aet", "optimal_cadence", "eftp")) {
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun launch() = Robolectric.buildActivity(ReadinessActivity::class.java).setup().get()

    @Test
    fun `with no history the summaries show their empty state`() {
        val a = launch()
        assertEquals(
            context.getString(R.string.readiness_no_history),
            a.findViewById<TextView>(R.id.readiness_verdict).text.toString(),
        )
        assertEquals(
            context.getString(R.string.readiness_aet_none),
            a.findViewById<TextView>(R.id.readiness_aet).text.toString(),
        )
        assertEquals(
            context.getString(R.string.readiness_cadence_none),
            a.findViewById<TextView>(R.id.readiness_cadence).text.toString(),
        )
        assertEquals(
            context.getString(R.string.readiness_eftp_none),
            a.findViewById<TextView>(R.id.readiness_eftp).text.toString(),
        )
    }

    @Test
    fun `stored history renders the populated summaries`() {
        val now = System.currentTimeMillis()
        ReadinessStore(context).record(now, 62f)
        AerobicThresholdStore(context).record(now, 205f, 120)
        OptimalCadenceStore(context).record(now, 92f, 500)
        EftpStore(context).record(now, 264f)

        val a = launch()

        assertTrue(a.findViewById<TextView>(R.id.readiness_aet).text.toString().contains("205"))
        assertTrue(a.findViewById<TextView>(R.id.readiness_cadence).text.toString().contains("92"))
        assertTrue(a.findViewById<TextView>(R.id.readiness_eftp).text.toString().contains("264"))
        assertTrue(
            a.findViewById<TextView>(R.id.readiness_verdict).text.toString() !=
                context.getString(R.string.readiness_no_history),
        )
    }

    @Test
    fun `tapping start without permissions requests them and leaves start enabled`() {
        val a = launch()
        a.findViewById<Button>(R.id.readiness_start).performClick()
        // Permissions aren't granted under Robolectric, so the measurement never
        // starts and the button stays enabled (no BLE access attempted).
        assertTrue(a.findViewById<Button>(R.id.readiness_start).isEnabled)
    }
}
