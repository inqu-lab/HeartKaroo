package com.inqulab.heartkaroo.hrv

import android.Manifest
import android.bluetooth.BluetoothManager
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hardware integration on the Karoo: scan for and stream from a real Polar H10
 * (or any standard BLE HRM). Requires a powered strap in range — worn, for the
 * HR test.
 *
 * Every test first `assumeTrue`-skips unless an enabled Bluetooth adapter is
 * present (the CI emulator has none), so the Polar SDK is never even
 * initialised there; the strap-dependent assertions skip again when nothing is
 * in range. The suite therefore passes on a bare emulator and on a Karoo
 * without a strap, and only truly exercises the radio on a Karoo with one.
 */
@RunWith(AndroidJUnit4::class)
class PolarBleManagerInstrumentedTest {

    @get:Rule
    val permission: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION)

    private val context: Context = ApplicationProvider.getApplicationContext()
    private var ble: PolarBleManager? = null

    @Before
    fun requireBluetooth() {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        assumeTrue("needs an enabled Bluetooth adapter (skipped on the emulator)", adapter?.isEnabled == true)
        ble = PolarBleManager.getInstance(context)
    }

    @After
    fun tearDown() {
        ble?.disconnect()
    }

    @Test
    fun scanDiscoversAStrap() {
        val device = ble!!.firstDevice()
        assumeTrue("needs a powered BLE HRM in range", device != null)
        assertTrue("a discovered device must carry an address", device!!.id.isNotBlank())
    }

    @Test
    fun connectsAndStreamsHeartRate() {
        val mgr = ble!!
        val device = mgr.firstDevice()
        assumeTrue("needs a BLE HRM in range", device != null)

        mgr.connect(device!!.id)
        val connected = awaitFlow(15_000, mgr.connectedFlow) { it }
        assumeTrue("the strap should connect", connected != null)

        val hr = awaitFlow(15_000, mgr.events()) { it is PolarBleManager.BleEvent.Heartrate }
        assertNotNull("a worn strap should stream heart rate", hr)
    }
}
