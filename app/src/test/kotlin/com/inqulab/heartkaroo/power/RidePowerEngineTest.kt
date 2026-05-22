package com.inqulab.heartkaroo.power

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Drives RidePowerEngine's per-sample handlers directly (bypassing the live
 * Karoo stream collectors) and asserts the StateFlow values, so the wiring
 * from each input to the right calculator and output flow is verified
 * without a device. The individual calculators have their own formula tests;
 * here we check the engine routes and exposes them correctly.
 */
@RunWith(RobolectricTestRunner::class)
class RidePowerEngineTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        // Use the default rider settings (FTP 270, CP 250, W′ 20000).
        ctx.getSharedPreferences("rider", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun newEngine() = RidePowerEngine(
        KarooSystemService(ctx),
        RiderSettings(ctx),
        MutableStateFlow<Float?>(null),
        CoroutineScope(Dispatchers.Unconfined),
    )

    /** Feed t = 0..[seconds] s of constant power at 1 Hz. */
    private fun RidePowerEngine.feedConstant(seconds: Int, watts: Double) {
        for (i in 0..seconds) onPower(i * 1000L, watts)
    }

    @Test
    fun `steady 200W with HR produces the expected power and HR metrics`() {
        val e = newEngine()
        e.latestHr = 150.0
        e.feedConstant(600, 200.0) // 10 min — past the EF / decoupling warmup

        assertEquals(1.0f, e.variabilityIndex.value!!, 0.02f)        // NP / AP
        assertEquals(200f / 270f, e.intensityFactor.value!!, 0.01f) // NP / FTP
        assertEquals(120f, e.kilojoules.value!!, 0.5f)              // 200 W × 600 s
        assertEquals(200f / 150f, e.efficiencyFactor.value!!, 0.02f) // NP / avg HR
        assertEquals(0.0f, e.decoupling.value!!, 0.01f)            // constant ratio
        assertNotNull(e.tss.value)
        assertTrue("TSS should accumulate", e.tss.value!! > 0f)
    }

    @Test
    fun `HR-gated metrics stay null until heart rate is present`() {
        val e = newEngine()
        e.feedConstant(600, 200.0) // no HR

        assertNull(e.efficiencyFactor.value)
        assertNull(e.cardiacCost.value)
        // Power-only metrics still compute.
        assertNotNull(e.intensityFactor.value)
        assertNotNull(e.kilojoules.value)
    }

    @Test
    fun `quadrant resolves from power and cadence`() {
        val e = newEngine()
        e.latestHr = 150.0
        e.latestCadence = 90.0
        e.feedConstant(60, 250.0)

        val q = e.quadrant.value
        assertNotNull(q)
        assertTrue("quadrant should be 1..4, was $q", q!! in 1f..4f)
    }

    @Test
    fun `VAM is altitude gain per hour over the window`() {
        val e = newEngine()
        e.onElevation(0L, 0.0)
        e.onElevation(60_000L, 100.0) // +100 m over 60 s -> 6000 m/h
        assertEquals(6000f, e.vam.value!!, 50f)
    }

    @Test
    fun `AeT surfaces an estimate once alpha falls with rising power`() {
        val e = newEngine()
        var t = 0L
        repeat(70) { e.onPower(t, 100.0); e.onAlpha(0.9f); t += 1000L }
        repeat(70) { e.onPower(t, 300.0); e.onAlpha(0.6f); t += 1000L }

        assertNotNull("AeT should resolve with a clear negative slope", e.aet.value)
        assertTrue("AeT estimate in a sane range, was ${e.aet.value}", e.aet.value!! in 50f..400f)
    }

    @Test
    fun `resetRide clears every per-ride metric`() {
        val e = newEngine()
        e.latestHr = 150.0
        e.feedConstant(600, 200.0)
        assertNotNull(e.kilojoules.value)
        assertNotNull(e.intensityFactor.value)

        e.resetRide()

        assertNull(e.kilojoules.value)
        assertNull(e.intensityFactor.value)
        assertNull(e.variabilityIndex.value)
        assertNull(e.tss.value)
        assertNull(e.efficiencyFactor.value)
        assertNull(e.decoupling.value)
        assertNull(e.aet.value)
    }
}
