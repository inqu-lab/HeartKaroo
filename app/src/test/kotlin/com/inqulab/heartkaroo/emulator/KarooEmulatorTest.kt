package com.inqulab.heartkaroo.emulator

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.inqulab.heartkaroo.power.RidePowerEngine
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Drives a full emulated ride (KarooEmulator → real RidePowerEngine + the
 * real HRV pipeline) and asserts every data field ends up with a plausible
 * physiological value — the closest thing to a ride on real hardware that
 * can run on the JVM. The end-of-ride values are also written as a readable
 * report to build/reports/karoo-emulator/ride-report.txt.
 */
@RunWith(RobolectricTestRunner::class)
class KarooEmulatorTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun clearSettings() {
        // Default rider settings (FTP 270, CP 250, W′ 20000).
        ctx.getSharedPreferences("rider", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `an emulated ride resolves every data field to a plausible value`() {
        val engine = RidePowerEngine(
            KarooSystemService(ctx),
            RiderSettings(ctx),
            MutableStateFlow<Float?>(null),
            CoroutineScope(Dispatchers.Unconfined),
        )
        val strap = EmulatedStrapPipeline()
        val emulator = KarooEmulator()

        // Snapshot α1/decoupling at the end of each long phase: the trajectory
        // (easy → hard → easy) is what the DFA field and AeT calibrator key on.
        val checkpointTicks = buildMap {
            var end = 0
            for (phase in emulator.profile) {
                end += phase.durationS
                if (phase.durationS >= 240) put(end - 1, phase)
            }
        }
        val checkpoints = LinkedHashMap<KarooEmulator.Phase, Checkpoint>()

        var peakVam = 0f
        var tickIndex = 0
        for (tick in emulator.ride()) {
            engine.latestHr = tick.hrBpm
            engine.latestCadence = tick.cadenceRpm
            engine.onPower(tick.tMs, tick.powerW)
            engine.onSpeed(tick.tMs, tick.speedMps)
            engine.onElevation(tick.tMs, tick.elevationGainM)
            strap.onRrIntervals(tick.tMs, tick.rrIntervalsMs)
            strap.dfaAlpha1?.let { engine.onAlpha(it) }
            engine.vam.value?.let { if (it > peakVam) peakVam = it }
            checkpointTicks[tickIndex]?.let { phase ->
                checkpoints[phase] = Checkpoint(
                    minute = (tickIndex + 1) / 60,
                    dfaAlpha1 = strap.dfaAlpha1,
                    decoupling = engine.decoupling.value,
                )
            }
            tickIndex++
        }

        val report = renderReport(engine, strap, peakVam, emulator.totalSeconds, checkpoints)
        val out = File("build/reports/karoo-emulator/ride-report.txt")
        out.parentFile?.mkdirs()
        out.writeText(report)
        println(report)

        // Pace/power-vs-HR fields. The final decoupling values are dominated by
        // the ride's structure (intervals, climb, cooldown), so the meaningful
        // steady-state check is the end-of-endurance checkpoint; the end-of-ride
        // values just have to stay finite and sane.
        val phases = checkpoints.entries.toList()
        val endurance = phases[1].value
        assertRange("steady-state Pw:Hr decoupling %", endurance.decoupling, -15f, 15f)
        assertRange("final Pw:Hr decoupling %", engine.decoupling.value, -40f, 60f)
        assertRange("final Pa:Hr decoupling %", engine.paHrDecoupling.value, -40f, 60f)
        assertRange("efficiency factor", engine.efficiencyFactor.value, 0.8f, 2.5f)
        assertRange("cardiac cost", engine.cardiacCost.value, 0.3f, 1.5f)
        assertRange("W' balance J", engine.wPrimeBalance.value, 0f, 20_000f)
        assertRange("AeT estimate W", engine.aet.value, 100f, 300f)

        // DFA α1 must read aerobic (high) after the warmup and drop with
        // intensity by the end of the climb — the gradient the AeT fit needs.
        val warmupAlpha = phases[0].value.dfaAlpha1
        val climbAlpha = phases[2].value.dfaAlpha1
        assertRange("DFA α1 after warmup", warmupAlpha, 0.7f, 1.4f)
        assertRange("DFA α1 at end of climb", climbAlpha, 0.3f, 0.85f)
        assertTrue(
            "α1 should fall with intensity (warmup $warmupAlpha vs climb $climbAlpha)",
            warmupAlpha!! > climbAlpha!!,
        )

        // Power analytics.
        assertRange("variability index", engine.variabilityIndex.value, 1f, 1.6f)
        assertRange("intensity factor", engine.intensityFactor.value, 0.5f, 1.1f)
        assertRange("TSS", engine.tss.value, 20f, 150f)
        assertRange("kilojoules", engine.kilojoules.value, 400f, 900f)
        assertRange("coasting %", engine.coasting.value, 0.5f, 10f)
        assertRange("quadrant", engine.quadrant.value, 1f, 4f)
        assertRange("optimal cadence rpm", engine.optimalCadence.value, 60f, 110f)
        assertRange("peak VAM m/h", peakVam, 700f, 1700f)

        // MMP curve must be monotonically non-increasing with duration. (The
        // 60-min window resolves on a 59-min ride because MmpCalculator counts
        // a ≥90%-spanned window as full.)
        val mmp5s = engine.mmpFlow("mmp_5s").value
        val mmp1m = engine.mmpFlow("mmp_1min").value
        val mmp5m = engine.mmpFlow("mmp_5min").value
        val mmp20m = engine.mmpFlow("mmp_20min").value
        val mmp60m = engine.mmpFlow("mmp_60min").value
        assertRange("MMP 5s", mmp5s, 300f, 600f)
        assertNotNull("MMP 60min", mmp60m)
        assertTrue(
            "MMP must not increase with duration",
            mmp5s!! >= mmp1m!! && mmp1m >= mmp5m!! && mmp5m >= mmp20m!! && mmp20m >= mmp60m!!,
        )

        // HRV fields from the emulated strap.
        assertRange("RMSSD ms", strap.rmssd, 1f, 200f)
        assertRange("HRV stress %", strap.stressPct, 0f, 100f)
        assertRange("DFA α1", strap.dfaAlpha1, 0.2f, 1.5f)
        assertRange("SDNN ms", strap.sdnn, 1f, 200f)
        assertRange("pNN50 %", strap.pnn50, 0f, 100f)
        assertRange("SD1 ms", strap.sd1, 0.5f, 100f)
        assertRange("SD2 ms", strap.sd2, 0.5f, 200f)
        assertRange("SD1/SD2", strap.sd1Sd2Ratio, 0.01f, 3f)
        assertRange("respiratory rate brpm", strap.respiratoryRate, 6f, 40f)
        assertRange("ectopic rate /min", strap.ectopicRate, 0f, 5f)

        // After-ride session summary values.
        assertNotNull("VT2 estimate", engine.vt2CurrentEstimate())
        assertNotNull("W' min", engine.wPrimeMinJ())
        val dist = engine.quadrantDistribution()
        assertNotNull("quadrant distribution", dist)
    }

    @Test
    fun `the emulator is deterministic for a given seed`() {
        val a = KarooEmulator(seed = 7L).ride().take(120).toList()
        val b = KarooEmulator(seed = 7L).ride().take(120).toList()
        assertTrue("same seed must produce the same ride", a == b)
    }

    private class Checkpoint(val minute: Int, val dfaAlpha1: Float?, val decoupling: Float?)

    private fun assertRange(name: String, value: Float?, min: Float, max: Float) {
        assertNotNull("$name should resolve by ride end", value)
        assertTrue("$name = $value outside plausible range [$min, $max]", value!! in min..max)
    }

    private fun renderReport(
        engine: RidePowerEngine,
        strap: EmulatedStrapPipeline,
        peakVam: Float,
        totalSeconds: Int,
        checkpoints: Map<KarooEmulator.Phase, Checkpoint>,
    ): String {
        fun f(v: Float?, digits: Int = 1, unit: String = ""): String =
            if (v == null) "--" else "%.${digits}f%s".format(v, unit)
        val dist = engine.quadrantDistribution()
        return buildString {
            appendLine("=".repeat(64))
            appendLine(" HeartKaroo — emulated ride report (${totalSeconds / 60} min, no hardware)")
            appendLine("=".repeat(64))
            appendLine(" Pace / power vs heart rate")
            appendLine("   Pw:Hr decoupling        ${f(engine.decoupling.value, 1, " %")}")
            appendLine("   Pa:Hr decoupling        ${f(engine.paHrDecoupling.value, 1, " %")}")
            appendLine("   Efficiency factor       ${f(engine.efficiencyFactor.value, 2)}")
            appendLine("   Cardiac cost            ${f(engine.cardiacCost.value, 2, " bpm/W")}")
            appendLine(
                "   W' balance              ${f(engine.wPrimeBalance.value, 0, " J")}" +
                    "   (min ${"%.0f".format(engine.wPrimeMinJ() ?: Double.NaN)} J," +
                    " matches burned ${engine.matchesBurnedCount})"
            )
            appendLine("   Cardiac pop             ${f(engine.cardiacPop.value, 0, " min")}")
            appendLine(
                "   AeT estimate            ${f(engine.aet.value, 0, " W")}" +
                    "   (VT2 ~ ${f(engine.vt2CurrentEstimate(), 0, " W")})"
            )
            appendLine(" Power analytics")
            appendLine("   Variability index       ${f(engine.variabilityIndex.value, 2)}")
            appendLine("   Intensity factor        ${f(engine.intensityFactor.value, 2)}")
            appendLine("   TSS                     ${f(engine.tss.value, 1)}")
            appendLine("   Kilojoules              ${f(engine.kilojoules.value, 0, " kJ")}")
            appendLine("   Coasting                ${f(engine.coasting.value, 1, " %")}")
            appendLine("   Quadrant (last 60 s)    ${f(engine.quadrant.value, 0)}")
            if (dist != null) {
                appendLine(
                    "   Quadrant distribution   " +
                        dist.withIndex().joinToString("  ") { (i, p) -> "Q${i + 1} ${"%.0f".format(p)}%" }
                )
            }
            appendLine(
                "   MMP 5s/1m/5m/20m/60m    " +
                    listOf("mmp_5s", "mmp_1min", "mmp_5min", "mmp_20min", "mmp_60min")
                        .joinToString(" / ") { f(engine.mmpFlow(it).value, 0) } + " W"
            )
            appendLine("   VAM (peak)              ${f(peakVam, 0, " m/h")}")
            appendLine("   Optimal cadence         ${f(engine.optimalCadence.value, 0, " rpm")}")
            appendLine(" HRV (emulated Polar strap)")
            appendLine("   RMSSD                   ${f(strap.rmssd, 1, " ms")}")
            appendLine("   Stress                  ${f(strap.stressPct, 0, " %")}")
            appendLine("   DFA α1                  ${f(strap.dfaAlpha1, 2)}")
            appendLine("   SDNN                    ${f(strap.sdnn, 1, " ms")}")
            appendLine("   pNN50                   ${f(strap.pnn50, 1, " %")}")
            appendLine(
                "   Poincaré SD1/SD2/ratio  ${f(strap.sd1, 1)} / ${f(strap.sd2, 1)} / ${f(strap.sd1Sd2Ratio, 2)}"
            )
            appendLine("   Respiratory rate        ${f(strap.respiratoryRate, 1, " brpm")}")
            appendLine("   Irregular beats         ${f(strap.ectopicRate, 1, " /min")}")
            appendLine(" Mid-ride checkpoints (end of each long phase)")
            for ((phase, cp) in checkpoints) {
                appendLine(
                    "   ${"%2d".format(cp.minute)} min @ ${"%3.0f".format(phase.powerW)} W" +
                        "        DFA α1 ${f(cp.dfaAlpha1, 2)}, Pw:Hr ${f(cp.decoupling, 1, " %")}"
                )
            }
            appendLine("=".repeat(64))
        }
    }
}
