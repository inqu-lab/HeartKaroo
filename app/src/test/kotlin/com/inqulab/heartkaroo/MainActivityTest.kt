package com.inqulab.heartkaroo

import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.inqulab.heartkaroo.settings.RiderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    @Test
    fun `launches and the buttons start their screens`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<Button>(R.id.open_readiness).performClick()
        activity.findViewById<Button>(R.id.open_settings).performClick()
        // Robolectric records the intents; both clicks should have launched something.
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `strap status says no strap paired when none is remembered`() {
        RiderSettings(ApplicationProvider.getApplicationContext()).pairedStrapMac = null
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(activity.mainLooper).idle()
        assertEquals(
            activity.getString(R.string.strap_status_none),
            activity.findViewById<TextView>(R.id.strap_status).text.toString(),
        )
    }

    @Test
    fun `strap status names the paired strap while not connected`() {
        RiderSettings(ApplicationProvider.getApplicationContext()).pairedStrapMac = "AA:BB:CC:DD:EE:FF"
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        shadowOf(activity.mainLooper).idle()
        assertEquals(
            activity.getString(R.string.strap_status_searching, "AA:BB:CC:DD:EE:FF"),
            activity.findViewById<TextView>(R.id.strap_status).text.toString(),
        )
    }
}
