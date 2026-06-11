package com.inqulab.heartkaroo.hrv

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device smoke test of the strap manager's observable state on an emulator
 * with no strap reachable. The BLE link itself can't be exercised without
 * hardware; what matters here is that the process-wide singleton holds, and
 * that the "which strap am I connected to?" surface (connected flag, device
 * name, battery) reads as fully disconnected — never a stale value that could
 * masquerade as a live strap on the Karoo.
 */
@RunWith(AndroidJUnit4::class)
class PolarBleManagerInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun managerIsOneInstancePerProcess() {
        val a = PolarBleManager.getInstance(context)
        val b = PolarBleManager.getInstance(context.applicationContext)
        assertSame(a, b)
    }

    @Test
    fun disconnectedManagerReportsNoStrapAndNoStaleValues() {
        val manager = PolarBleManager.getInstance(context)
        // Other tests in this process may have initiated a (doomed, emulator)
        // connection attempt; a deliberate disconnect must always land the
        // manager back in a clean state.
        manager.disconnect()

        assertFalse(manager.connectedFlow.value)
        assertNull(manager.connectedDeviceNameFlow.value)
        assertNull(manager.batteryFlow.value)
        assertEquals(0f, manager.rmssdFlow.value)
        assertNull(manager.stressFlow.value)
        assertNull(manager.dfaAlpha1Flow.value)
        assertNull(manager.sdnnFlow.value)
    }

    @Test
    fun dfaResetClearsThePublishedValue() {
        val manager = PolarBleManager.getInstance(context)
        manager.resetDfaAlpha1()
        assertNull(manager.dfaAlpha1Flow.value)
    }
}
