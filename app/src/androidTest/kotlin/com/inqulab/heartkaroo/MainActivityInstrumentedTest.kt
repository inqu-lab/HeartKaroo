package com.inqulab.heartkaroo

import android.Manifest
import android.content.Context
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.inqulab.heartkaroo.settings.RiderSettings
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device counterpart to the Robolectric MainActivityTest: drives the real
 * activity on an emulator (the CI `connectedDebugAndroidTest` job) and checks
 * the strap status line — the rider's quick answer to "is the right strap
 * connected?". No strap is reachable on an emulator, so the connected state
 * itself is exercised by the unit-level flow wiring; here we verify the
 * unpaired and paired-but-searching renderings against the real framework.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    // Pre-grant the BLE permissions so MainActivity's up-front request doesn't
    // pop a system dialog over the screen under test.
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
    fun strapStatusShowsPairFirstHintWhenNothingIsPaired() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.strap_status_none),
                    activity.findViewById<TextView>(R.id.strap_status).text.toString(),
                )
            }
        }
    }

    @Test
    fun strapStatusNamesThePairedStrapWhileSearching() {
        RiderSettings(context).pairedStrapMac = "AA:BB:CC:DD:EE:FF"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(
                    activity.getString(R.string.strap_status_searching, "AA:BB:CC:DD:EE:FF"),
                    activity.findViewById<TextView>(R.id.strap_status).text.toString(),
                )
            }
        }
    }
}
