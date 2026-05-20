package com.inqulab.heartkaroo

import com.inqulab.heartkaroo.climb.VamCalculator
import com.inqulab.heartkaroo.decoupling.DecouplingCalculator
import com.inqulab.heartkaroo.efficiency.CardiacCostCalculator
import com.inqulab.heartkaroo.efficiency.EfficiencyFactorCalculator
import com.inqulab.heartkaroo.hrv.DfaAlpha1Calculator
import com.inqulab.heartkaroo.hrv.HRVCalculator
import com.inqulab.heartkaroo.hrv.Pnn50Calculator
import com.inqulab.heartkaroo.hrv.PoincareCalculator
import com.inqulab.heartkaroo.hrv.RespiratoryRateCalculator
import com.inqulab.heartkaroo.hrv.SdnnCalculator
import com.inqulab.heartkaroo.power.CoastingCalculator
import com.inqulab.heartkaroo.power.KilojoulesCalculator
import com.inqulab.heartkaroo.power.MmpCalculator
import com.inqulab.heartkaroo.power.NormalizedPowerCalculator
import com.inqulab.heartkaroo.power.QuadrantAnalysisCalculator
import com.inqulab.heartkaroo.wprime.WPrimeBalanceCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Golden-master regression test for the pure metric calculators.
 *
 * Two deterministic, synthetic fixtures live in `src/test/resources/golden/`:
 *   - rr_intervals.csv  : a ~480-beat RR series with respiratory-sinus-arrhythmia
 *                         oscillation, a slow upward HR trend and seeded noise.
 *   - ride.csv          : a 40-min, 1 Hz ride (warmup, 4×interval block, coast,
 *                         steady) with power, heart rate, cadence and a climb.
 *
 * The expected outputs are frozen alongside them as `*_golden.csv`. The test
 * replays each fixture through the calculators and asserts the results still
 * match the golden values (within a small tolerance), so any change that moves
 * a calculator's output is caught.
 *
 * To regenerate the fixtures AND the goldens after an intentional change:
 *   ./gradlew :app:testDebugUnitTest --tests '*GoldenReferenceTest' \
 *       -Dgolden.regenerate=true
 * then review the CSV diff before committing.
 */
class GoldenReferenceTest {

    private val regenerate = System.getProperty("golden.regenerate") == "true"

    @Test
    fun `HRV metrics match the golden reference`() {
        val rr = if (regenerate) generateRr().also { writeRrCsv(it) } else readRrCsv()
        val actual = computeHrv(rr)
        checkOrWrite("hrv_golden.csv", actual)
    }

    @Test
    fun `ride power and HR metrics match the golden reference`() {
        val ride = if (regenerate) generateRide().also { writeRideCsv(it) } else readRideCsv()
        val actual = computeRide(ride)
        checkOrWrite("ride_golden.csv", actual)
    }

    // ---- calculators under test -------------------------------------------

    private fun computeHrv(rr: List<Int>): Map<String, Double?> {
        val n = rr.size
        val rmssd = HRVCalculator(windowSize = n).apply { rr.forEach { addInterval(it) } }.getRmssd()
        val sdnn = SdnnCalculator(windowSize = n).apply { rr.forEach { addInterval(it) } }.getSdnn()
        val pnn50 = Pnn50Calculator(windowSize = n).apply { rr.forEach { addInterval(it) } }.getPnn50()
        val poincare = PoincareCalculator(windowSize = n).apply { rr.forEach { addInterval(it) } }.getResult()
        val alpha1 = DfaAlpha1Calculator(minSamples = 120, maxSamples = n)
            .apply { rr.forEach { addInterval(it) } }.getAlpha1()
        val resp = RespiratoryRateCalculator(windowSize = n).apply { rr.forEach { addInterval(it) } }.getBreathsPerMin()
        return linkedMapOf(
            "rmssd" to rmssd.toDouble(),
            "sdnn" to sdnn?.toDouble(),
            "pnn50" to pnn50?.toDouble(),
            "poincare_sd1" to poincare?.sd1?.toDouble(),
            "poincare_sd2" to poincare?.sd2?.toDouble(),
            "poincare_ratio" to poincare?.ratio?.toDouble(),
            "dfa_alpha1" to alpha1?.toDouble(),
            "resp_bpm" to resp?.toDouble(),
        )
    }

    private fun computeRide(ride: List<RideSample>): Map<String, Double?> {
        val np = NormalizedPowerCalculator()
        val mmp5s = MmpCalculator(5_000L)
        val mmp1m = MmpCalculator(60_000L)
        val mmp5m = MmpCalculator(300_000L)
        val mmp20m = MmpCalculator(1_200_000L)
        val kj = KilojoulesCalculator()
        val decoupling = DecouplingCalculator()
        val ef = EfficiencyFactorCalculator()
        val cc = CardiacCostCalculator()
        val wprime = WPrimeBalanceCalculator(criticalPowerW = 250.0, wPrimeJ = 20_000.0)
        val coasting = CoastingCalculator()
        val vam = VamCalculator()
        val quadrant = QuadrantAnalysisCalculator(ftpW = 265.0)

        var wFinal = 20_000.0
        var wMin = Double.MAX_VALUE
        var vamMax = Double.NEGATIVE_INFINITY

        for (s in ride) {
            np.add(s.timeMs, s.powerW)
            mmp5s.add(s.timeMs, s.powerW)
            mmp1m.add(s.timeMs, s.powerW)
            mmp5m.add(s.timeMs, s.powerW)
            mmp20m.add(s.timeMs, s.powerW)
            kj.add(s.timeMs, s.powerW)
            decoupling.add(s.timeMs, s.powerW, s.hrBpm)
            ef.add(s.timeMs, s.powerW, s.hrBpm)
            cc.add(s.timeMs, s.powerW, s.hrBpm)
            val b = wprime.add(s.timeMs, s.powerW).toDouble()
            wFinal = b
            if (b < wMin) wMin = b
            coasting.add(s.timeMs, s.powerW)
            vam.add(s.timeMs, s.elevationM)?.let { if (it.toDouble() > vamMax) vamMax = it.toDouble() }
            quadrant.add(s.timeMs, s.powerW, s.cadenceRpm)
        }

        return linkedMapOf(
            "average_power" to np.averagePower()?.toDouble(),
            "normalized_power" to np.normalizedPower()?.toDouble(),
            "mmp_5s" to mmp5s.current()?.toDouble(),
            "mmp_1min" to mmp1m.current()?.toDouble(),
            "mmp_5min" to mmp5m.current()?.toDouble(),
            "mmp_20min" to mmp20m.current()?.toDouble(),
            "kilojoules" to kj.current().toDouble(),
            "decoupling_pct" to decoupling.current(),
            "efficiency_factor" to ef.current()?.toDouble(),
            "cardiac_cost" to cc.current()?.toDouble(),
            "wprime_bal_final" to wFinal,
            "wprime_bal_min" to wMin,
            "coasting_pct" to coasting.current()?.toDouble(),
            "vam_max" to vamMax.takeIf { it != Double.NEGATIVE_INFINITY },
            "quadrant_dominant" to quadrant.dominantQuadrant()?.toDouble(),
        )
    }

    // ---- deterministic fixture generators ---------------------------------

    /** xorshift64 — platform-independent, fully reproducible pseudo-random. */
    private class Rng(seed: Long) {
        private var s = seed
        fun nextUnit(): Double {
            s = s xor (s shl 13); s = s xor (s ushr 7); s = s xor (s shl 17)
            return (s ushr 11).toDouble() / (1L shl 53).toDouble() * 2.0 - 1.0
        }
    }

    private fun generateRr(): List<Int> {
        val rng = Rng(0xC0FFEEL)
        return (0 until 480).map { i ->
            val trend = -0.18 * i                 // slow HR rise over the recording
            val rsa = 38.0 * sin(2 * PI * i / 5.0) // respiratory sinus arrhythmia
            val noise = 14.0 * rng.nextUnit()
            (950.0 + trend + rsa + noise).roundToInt().coerceIn(300, 2000)
        }
    }

    private data class RideSample(
        val timeMs: Long,
        val powerW: Double,
        val hrBpm: Double,
        val cadenceRpm: Double,
        val elevationM: Double,
    )

    private fun generateRide(): List<RideSample> {
        val rng = Rng(0xBEEFL)
        val out = ArrayList<RideSample>(2400)
        var hr = 95.0
        for (t in 0 until 2400) {
            val base = basePower(t)
            val power = (base + if (base > 5.0) 8.0 * rng.nextUnit() else 0.0).coerceAtLeast(0.0)
            val cadence = when {
                power < 5.0 -> 0.0
                power < 140.0 -> 85.0 + 2.0 * rng.nextUnit()
                else -> 92.0 + 2.0 * rng.nextUnit()
            }
            val elev = elevation(t)
            val target = 95.0 + 0.17 * power + 0.0022 * t
            hr += (target - hr) * 0.05
            out.add(
                RideSample(
                    timeMs = t * 1000L,
                    powerW = round2(power),
                    hrBpm = round2(hr.coerceIn(90.0, 190.0)),
                    cadenceRpm = round2(cadence),
                    elevationM = round3(elev),
                ),
            )
        }
        return out
    }

    private fun basePower(t: Int): Double = when {
        t < 300 -> 120.0                                            // warmup
        t < 1740 -> if ((t - 300) % 360 < 180) 285.0 else 130.0    // 4× (3min hard / 3min easy)
        t < 1800 -> 0.0                                             // 60 s coast
        else -> 185.0                                              // steady
    }

    private fun elevation(t: Int): Double = when {
        t < 600 -> 100.0
        t < 1200 -> 100.0 + (t - 600) * 0.25   // ~900 m/h climb
        else -> 250.0
    }

    // ---- CSV fixtures ------------------------------------------------------

    private fun writeRrCsv(rr: List<Int>) {
        val sb = StringBuilder("index,rr_ms\n")
        rr.forEachIndexed { i, v -> sb.append(i).append(',').append(v).append('\n') }
        goldenFile("rr_intervals.csv").writeText(sb.toString())
    }

    private fun readRrCsv(): List<Int> =
        readResourceLines("rr_intervals.csv").drop(1).map { it.substringAfter(',').trim().toInt() }

    private fun writeRideCsv(ride: List<RideSample>) {
        val sb = StringBuilder("t_ms,power_w,hr_bpm,cadence_rpm,elevation_m\n")
        for (s in ride) {
            sb.append(s.timeMs).append(',')
                .append(fmt(s.powerW, 2)).append(',')
                .append(fmt(s.hrBpm, 2)).append(',')
                .append(fmt(s.cadenceRpm, 2)).append(',')
                .append(fmt(s.elevationM, 3)).append('\n')
        }
        goldenFile("ride.csv").writeText(sb.toString())
    }

    private fun readRideCsv(): List<RideSample> =
        readResourceLines("ride.csv").drop(1).map { line ->
            val c = line.split(',')
            RideSample(c[0].toLong(), c[1].toDouble(), c[2].toDouble(), c[3].toDouble(), c[4].toDouble())
        }

    // ---- golden compare / write -------------------------------------------

    private fun checkOrWrite(name: String, actual: Map<String, Double?>) {
        if (regenerate) {
            val sb = StringBuilder("metric,value\n")
            for ((k, v) in actual) sb.append(k).append(',').append(v?.let { fmt(it, 6) } ?: "null").append('\n')
            goldenFile(name).writeText(sb.toString())
            return
        }
        val expected = readResourceLines(name).drop(1).associate { line ->
            val c = line.split(',')
            c[0] to (c[1].trim().takeIf { it != "null" }?.toDouble())
        }
        assertEquals("metric set drifted in $name", expected.keys, actual.keys)
        for ((k, exp) in expected) {
            val act = actual.getValue(k)
            if (exp == null) {
                assertNull("$k expected null but was $act", act)
            } else {
                assertNotNull("$k expected $exp but was null", act)
                val tol = maxOf(1e-3, 1e-4 * abs(exp))
                assertTrue(
                    "$k drifted: expected $exp, got $act (tol $tol)",
                    abs(exp - act!!) <= tol,
                )
            }
        }
    }

    // ---- io helpers --------------------------------------------------------

    private fun round2(x: Double) = (x * 100.0).roundToInt() / 100.0
    private fun round3(x: Double) = (x * 1000.0).roundToInt() / 1000.0
    private fun fmt(x: Double, decimals: Int) = String.format(Locale.ROOT, "%.${decimals}f", x)

    private fun goldenFile(name: String): File {
        val base = listOf(File("src/test/resources"), File("app/src/test/resources"))
            .firstOrNull { it.isDirectory } ?: File("src/test/resources")
        return File(base, "golden").apply { mkdirs() }.let { File(it, name) }
    }

    private fun readResourceLines(name: String): List<String> =
        (javaClass.getResourceAsStream("/golden/$name")
            ?: error("Missing /golden/$name on the test classpath. Regenerate with -Dgolden.regenerate=true"))
            .bufferedReader().readLines().filter { it.isNotBlank() }
}
