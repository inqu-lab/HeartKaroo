package com.inqulab.heartkaroo

import android.widget.Button
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
}
