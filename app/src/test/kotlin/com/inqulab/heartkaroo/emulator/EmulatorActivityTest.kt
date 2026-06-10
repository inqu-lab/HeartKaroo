package com.inqulab.heartkaroo.emulator

import android.os.Looper
import android.widget.Button
import android.widget.TextView
import com.inqulab.heartkaroo.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EmulatorActivityTest {

    @Test
    fun `launches ready with an empty dashboard and the ride length`() {
        val activity = Robolectric.buildActivity(EmulatorActivity::class.java).setup().get()
        val status = activity.findViewById<TextView>(R.id.emulator_status).text.toString()
        assertTrue("status should show the ride length, was: $status", status.contains("59:00"))
        val dash = activity.findViewById<TextView>(R.id.emulator_dashboard).text.toString()
        // Nothing played yet — every field still searching.
        assertTrue(Regex("TSS\\s+--").containsMatchIn(dash))
        assertEquals(
            activity.getString(R.string.emulator_start),
            activity.findViewById<Button>(R.id.emulator_play).text.toString(),
        )
    }

    @Test
    fun `playing fills the dashboard and pause keeps the position`() {
        val activity = Robolectric.buildActivity(EmulatorActivity::class.java).setup().get()
        val play = activity.findViewById<Button>(R.id.emulator_play)

        play.performClick()
        // The playback loop runs on Dispatchers.Default in real time (~60
        // simulated s/s at the default 30× after the per-tick delay); give it a
        // moment, then drain the UI updates it posted to the main looper.
        Thread.sleep(1_500)
        shadowOf(Looper.getMainLooper()).idle()

        val dash = activity.findViewById<TextView>(R.id.emulator_dashboard).text.toString()
        assertTrue(
            "power-only fields should resolve once the ride is playing:\n$dash",
            Regex("Kilojoules\\s+\\d").containsMatchIn(dash),
        )
        val status = activity.findViewById<TextView>(R.id.emulator_status).text.toString()
        assertTrue("status should show ride progress, was: $status", status.contains("/ 59:00"))

        play.performClick() // pause
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(activity.getString(R.string.emulator_resume), play.text.toString())
    }
}
