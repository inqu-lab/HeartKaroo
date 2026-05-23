package com.inqulab.heartkaroo.power

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.inqulab.heartkaroo.awaitFlow
import com.inqulab.heartkaroo.connectBlocking
import com.inqulab.heartkaroo.isEmulator
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end engine test on the Karoo: wire RidePowerEngine to the live
 * KarooSystemService streams and confirm it accumulates a metric from real
 * power data. Requires an active ride (or the Karoo sensor simulator) producing
 * power; otherwise it is skipped. The pure metric maths are covered off-device
 * in RidePowerEngineTest — this proves the live stream wiring.
 */
@RunWith(AndroidJUnit4::class)
class RidePowerEngineInstrumentedTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var karoo: KarooSystemService? = null

    @Before
    fun requireKarooHardware() {
        assumeFalse("needs a real Karoo; skipped on the emulator", isEmulator())
    }

    @After
    fun tearDown() {
        scope.cancel()
        karoo?.disconnect()
    }

    @Test
    fun accumulatesKilojoulesFromLivePower() {
        val k = KarooSystemService(context).also { karoo = it }
        assumeTrue("needs a Karoo connection", k.connectBlocking())

        val engine = RidePowerEngine(k, RiderSettings(context), MutableStateFlow(null), scope)
        engine.start()

        val powering = awaitFlow(8_000, k.streamDataFlow(DataType.Type.POWER)) {
            it is StreamState.Streaming
        }
        assumeTrue("needs an active ride streaming power", powering != null)

        val kj = awaitFlow(15_000, engine.kilojoules) { it != null }
        assertNotNull("the engine should accumulate kJ from live power", kj)
    }
}
