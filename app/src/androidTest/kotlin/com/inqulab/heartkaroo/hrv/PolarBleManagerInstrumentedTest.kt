package com.inqulab.heartkaroo.hrv

import android.Manifest
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.inqulab.heartkaroo.awaitFlow
import com.inqulab.heartkaroo.firstDevice
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware integration on the Karoo: scan for and stream from a real Polar H10
 * (or any standard BLE HRM). Requires a powered strap in range — worn, for the
 * HR test. Both tests skip (assumeTrue) when no strap is found so the suite
 * still passes on a bare Karoo.
 */
@RunWith(AndroidJUnit4::class)
class PolarBleManagerInstrumentedTest {

    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val ble: PolarBleManager by lazy { PolarBleManager.getInstance(context) }

    @After
    fun tearDown() {
        ble.disconnect()
    }

    @Test
    fun scanDiscoversAStrap() {
        val device = ble.firstDevice()
        assumeTrue("needs a powered BLE HRM in range", device != null)
        assertTrue("a discovered device must carry an address", device!!.id.isNotBlank())
    }

    @Test
    fun connectsAndStreamsHeartRate() {
        val device = ble.firstDevice()
        assumeTrue("needs a BLE HRM in range", device != null)

        ble.connect(device!!.id)
        val connected = awaitFlow(15_000, ble.connectedFlow) { it }
        assumeTrue("the strap should connect", connected != null)

        val hr = awaitFlow(15_000, ble.events()) { it is PolarBleManager.BleEvent.Heartrate }
        assertNotNull("a worn strap should stream heart rate", hr)
    }
}
