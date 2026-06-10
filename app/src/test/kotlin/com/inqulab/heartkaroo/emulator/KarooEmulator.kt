package com.inqulab.heartkaroo.emulator

import java.util.Random
import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Emulates everything the extension normally gets from hardware: the Karoo's
 * power / HR / cadence / speed / elevation-gain streams and the Polar strap's
 * RR-interval stream — so a whole ride can be driven through the real
 * [com.inqulab.heartkaroo.power.RidePowerEngine] and the real HRV pipeline
 * with no Karoo, trainer or strap.
 *
 * One [Tick] per simulated second carries what the Karoo would stream plus
 * the RR intervals the strap would have delivered during that second. The
 * rider model is deliberately simple but physiologically shaped:
 *
 *  - HR chases a power-proportional target with a first-order lag, plus a
 *    slow upward cardiac drift (so decoupling reads positive late in a ride).
 *  - RR intervals are 60000/HR plus respiratory sinus arrhythmia (a breathing
 *    sinusoid whose rate rises and amplitude collapses with intensity), a slow
 *    Mayer-wave oscillation, and beat-to-beat noise — all of which shrink and
 *    whiten as intensity rises, so RMSSD collapses and DFA α1 falls from ~1.0
 *    toward 0.5 as the ride gets harder, which is exactly the behaviour the
 *    DFA field and the AeT calibrator expect.
 *  - A small fraction of beats are corrupted (premature-beat style) to
 *    exercise the artifact corrector and the ectopic counter.
 *
 * Deterministic for a given [seed].
 */
class KarooEmulator(
    seed: Long = 42L,
    private val ftpW: Double = 270.0,
    val profile: List<Phase> = DEFAULT_RIDE,
) {
    /** [powerW] = 0 simulates coasting; [gradePct] > 0 climbs (slower, gains elevation). */
    data class Phase(val durationS: Int, val powerW: Double, val gradePct: Double = 0.0)

    /** One second of emulated Karoo streams + the strap RRs that fell inside it. */
    data class Tick(
        val tMs: Long,
        val powerW: Double,
        val hrBpm: Double,
        val cadenceRpm: Double,
        val speedMps: Double,
        val elevationGainM: Double,
        val rrIntervalsMs: List<Int>,
    )

    companion object {
        /** 59-min ride: warmup, 4× 1-min VO2 intervals (with a coast after each),
         *  a long steady endurance block (so decoupling has a steady window to
         *  judge, and so DFA α1 — a ~5-min trailing window — recovers from the
         *  intervals before the α1-vs-power AeT fit accumulates its easy pairs),
         *  an 8-min 6% climb, cooldown. */
        val DEFAULT_RIDE: List<Phase> = buildList {
            add(Phase(600, 150.0))
            repeat(4) {
                add(Phase(60, 400.0))
                add(Phase(20, 0.0))
                add(Phase(100, 120.0))
            }
            add(Phase(1500, 210.0))
            add(Phase(480, 240.0, gradePct = 6.0))
            add(Phase(240, 110.0))
        }

        private const val REST_HR = 60.0
        private const val MAX_HR = 190.0
        private const val HR_LAG_TAU_S = 35.0
        private const val CARDIAC_DRIFT_PER_HOUR = 0.05
        private const val ARTIFACT_PROBABILITY = 0.004
        private const val RIDER_MASS_KG = 78.0
        // Mayer waves: the ~0.1 Hz blood-pressure oscillation visible in RR.
        private const val MAYER_PERIOD_S = 10.0
        // Where the emulated rider's aerobic threshold sits relative to FTP.
        private const val AET_FRACTION_OF_FTP = 0.74
    }

    private val rng = Random(seed)

    val totalSeconds: Int = profile.sumOf { it.durationS }

    fun ride(): Sequence<Tick> = sequence {
        var hr = 75.0
        var speed = 0.0
        var elevation = 0.0
        var breathPhase = 0.0
        var mayerPhase = 0.0
        var jitter = 0.0
        var nextBeatAtMs = 0.0
        var t = 0
        for (phase in profile) {
            repeat(phase.durationS) {
                val tMs = t * 1000L
                val coasting = phase.powerW < 1.0
                val power = if (coasting) 0.0 else {
                    (phase.powerW + rng.nextGaussian() * phase.powerW * 0.07).coerceAtLeast(0.0)
                }

                val drift = 1.0 + CARDIAC_DRIFT_PER_HOUR * (t / 3600.0)
                val hrTarget = ((90.0 + phase.powerW / ftpW * 85.0) * drift).coerceAtMost(MAX_HR - 2.0)
                hr += (hrTarget - hr) / HR_LAG_TAU_S + rng.nextGaussian() * 0.4
                hr = hr.coerceIn(REST_HR, MAX_HR)

                val cadence = if (coasting) 0.0 else {
                    (92.0 - phase.gradePct * 2.0 + rng.nextGaussian() * 2.0).coerceAtLeast(60.0)
                }

                speed = when {
                    coasting -> speed * 0.97
                    // Climbing: nearly all power goes into lifting rider+bike.
                    phase.gradePct > 0.5 ->
                        power / (RIDER_MASS_KG * 9.81 * phase.gradePct / 100.0 * 1.15)
                    // Flat: aero-dominated, v ∝ ∛power.
                    else -> cbrt(power / 0.26)
                }.coerceIn(0.0, 22.0)
                if (phase.gradePct > 0.0) elevation += speed * phase.gradePct / 100.0

                // Strap beats that land inside this second. Breathing follows the
                // (lagged) HR; the RR fluctuation *structure* follows the power,
                // anchored to the rider's aerobic threshold (~74% of FTP): below
                // it the slow Mayer wave and strongly correlated beat noise
                // dominate (α1 toward 1), above it they fade and whiten (α1
                // toward 0.5) — which is what makes the AeT fit land near AET_W.
                val hrIntensity = ((hr - REST_HR) / (MAX_HR - REST_HR)).coerceIn(0.0, 1.0)
                val aetFraction = phase.powerW / (AET_FRACTION_OF_FTP * ftpW)
                val correlation = ((1.30 - aetFraction) / 0.55).coerceIn(0.0, 1.0)
                val breathRatePerMin = 12.0 + 22.0 * hrIntensity
                val rsaAmpMs = (30.0 * (1.0 - hrIntensity)).coerceAtLeast(3.0)
                // Quadratic: a slow wave survives DFA's per-box detrending far
                // better than white noise, so even a modest amplitude dominates
                // α1 — it has to die off quickly as intensity rises. Sized so
                // easy-riding α1 sits near 1.05: above 1.20 the AeT calibrator
                // discards the sample, which would starve the fit of easy pairs.
                val mayerAmpMs = 11.0 * correlation * correlation
                val jitterSd = 3.0 + 5.0 * correlation
                val phi = (0.92 * correlation).coerceAtMost(0.90)
                val rrs = ArrayList<Int>(3)
                val tickEndMs = tMs + 1000.0
                while (nextBeatAtMs < tickEndMs) {
                    val baseMs = 60_000.0 / hr
                    breathPhase += 2.0 * PI * (breathRatePerMin / 60.0) * (baseMs / 1000.0)
                    mayerPhase += 2.0 * PI * (baseMs / 1000.0) / MAYER_PERIOD_S
                    jitter = phi * jitter + sqrt(1.0 - phi * phi) * jitterSd * rng.nextGaussian()
                    var rr = baseMs + rsaAmpMs * sin(breathPhase) + mayerAmpMs * sin(mayerPhase) + jitter
                    if (rng.nextDouble() < ARTIFACT_PROBABILITY) rr *= 0.55 // premature beat
                    rrs.add(rr.toInt().coerceIn(250, 2200))
                    nextBeatAtMs += rr
                }
                yield(Tick(tMs, power, hr, cadence, speed, elevation, rrs))
                t++
            }
        }
    }
}
