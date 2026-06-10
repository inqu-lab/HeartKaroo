package com.inqulab.heartkaroo.readiness

import android.Manifest
import android.content.Context
import android.widget.Button
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.inqulab.heartkaroo.R
import com.inqulab.heartkaroo.settings.RiderSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device test of the Readiness screen's pre-measurement states on an
 * emulator (no strap reachable): the screen must refuse to start without a
 * paired strap, and must NAME the strap (by its stored MAC) while connecting so
 * the rider can spot a wrong strap before the 2-minute reading begins.
 */
@RunWith(AndroidJUnit4::class)
class ReadinessActivityInstrumentedTest {

    @get:Rule
    val blePermissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPairedStrap() {
        RiderSettings(context).pairedStrapMac = null
    }

    @Test
    fun startWithoutPairedStrapAsksToPairFirstAndKeepsButtonEnabled() {
        ActivityScenario.launch(ReadinessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.readiness_start).performClick()
            }
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.readiness_no_paired_strap),
                    activity.findViewById<TextView>(R.id.readiness_status).text.toString(),
                )
                assertTrue(activity.findViewById<Button>(R.id.readiness_start).isEnabled)
            }
        }
    }

    @Test
    fun startWithPairedStrapNamesItWhileConnecting() {
        RiderSettings(context).pairedStrapMac = "AA:BB:CC:DD:EE:FF"
        ActivityScenario.launch(ReadinessActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.findViewById<Button>(R.id.readiness_start).performClick()
            }
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.readiness_connecting_fmt, "AA:BB:CC:DD:EE:FF"),
                    activity.findViewById<TextView>(R.id.readiness_status).text.toString(),
                )
                assertFalse(activity.findViewById<Button>(R.id.readiness_start).isEnabled)
            }
        }
    }
}
