package com.inqulab.heartkaroo.emulator

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.inqulab.heartkaroo.R
import com.inqulab.heartkaroo.power.RidePowerEngine
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.KarooSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Ride Simulator" screen: plays a [KarooEmulator] ride through the real
 * [RidePowerEngine] and the real HRV pipeline and shows every data field
 * updating live — so the extension's metrics can be watched end-to-end with
 * no Karoo ride, trainer or strap. Playback is accelerated (selectable
 * speed); pausing keeps the ride position, restarting begins a fresh ride.
 *
 * The engine instance here is the screen's own (fed directly, like
 * KarooEmulatorTest does) — it never touches the extension service's
 * engine or a live ride.
 */
class EmulatorActivity : AppCompatActivity() {

    private lateinit var engine: RidePowerEngine
    private lateinit var strap: EmulatedStrapPipeline
    private lateinit var ticks: Iterator<KarooEmulator.Tick>
    private var totalSeconds = 0

    @Volatile
    private var lastTick: KarooEmulator.Tick? = null

    @Volatile
    private var speedIndex = DEFAULT_SPEED_INDEX
    private var playJob: Job? = null
    private var finished = false

    private lateinit var statusView: TextView
    private lateinit var streamsView: TextView
    private lateinit var dashboardView: TextView
    private lateinit var playButton: Button
    private lateinit var speedButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_emulator)
        statusView = findViewById(R.id.emulator_status)
        streamsView = findViewById(R.id.emulator_streams)
        dashboardView = findViewById(R.id.emulator_dashboard)
        playButton = findViewById(R.id.emulator_play)
        speedButton = findViewById(R.id.emulator_speed)
        playButton.setOnClickListener { togglePlay() }
        speedButton.setOnClickListener {
            speedIndex = (speedIndex + 1) % SPEEDS.size
            speedButton.text = getString(R.string.emulator_speed_fmt, SPEEDS[speedIndex])
        }
        speedButton.text = getString(R.string.emulator_speed_fmt, SPEEDS[speedIndex])
        resetRide()
    }

    private fun resetRide() {
        playJob?.cancel()
        playJob = null
        val settings = RiderSettings(applicationContext)
        // The screen's own engine, driven by the emulated streams; start() is
        // never called, so it doesn't collect from the (absent) Karoo system.
        engine = RidePowerEngine(
            KarooSystemService(applicationContext), settings,
            MutableStateFlow<Float?>(null), lifecycleScope,
        )
        strap = EmulatedStrapPipeline()
        val emulator = KarooEmulator(ftpW = settings.ftpW.toDouble())
        totalSeconds = emulator.totalSeconds
        ticks = emulator.ride().iterator()
        lastTick = null
        finished = false
        playButton.text = getString(R.string.emulator_start)
        render()
    }

    private fun togglePlay() {
        if (playJob != null) {
            playJob?.cancel()
            playJob = null
            playButton.text = getString(R.string.emulator_resume)
            render()
            return
        }
        if (finished) resetRide()
        playButton.text = getString(R.string.emulator_pause)
        playJob = lifecycleScope.launch(Dispatchers.Default) {
            var lastUiAt = 0L
            while (isActive && ticks.hasNext()) {
                val tick = ticks.next()
                engine.latestHr = tick.hrBpm
                engine.latestCadence = tick.cadenceRpm
                engine.onPower(tick.tMs, tick.powerW)
                engine.onSpeed(tick.tMs, tick.speedMps)
                engine.onElevation(tick.tMs, tick.elevationGainM)
                strap.onRrIntervals(tick.tMs, tick.rrIntervalsMs)
                strap.dfaAlpha1?.let { engine.onAlpha(it) }
                lastTick = tick
                // Wall clock, not SystemClock.elapsedRealtime(): Robolectric
                // freezes the latter, which would suppress every mid-ride render.
                val now = System.currentTimeMillis()
                if (now - lastUiAt >= UI_REFRESH_MS) {
                    lastUiAt = now
                    withContext(Dispatchers.Main) { render() }
                }
                delay(1000L / SPEEDS[speedIndex])
            }
            if (isActive) {
                withContext(Dispatchers.Main) {
                    finished = true
                    playJob = null
                    playButton.text = getString(R.string.emulator_restart)
                    render()
                }
            }
        }
    }

    private fun render() {
        val t = lastTick
        statusView.text = when {
            finished -> getString(R.string.emulator_finished_fmt, clock(totalSeconds))
            t == null -> getString(R.string.emulator_ready_fmt, clock(totalSeconds))
            else -> getString(
                R.string.emulator_progress_fmt,
                clock((t.tMs / 1000L).toInt() + 1), clock(totalSeconds),
            )
        }
        streamsView.text = if (t == null) "" else getString(
            R.string.emulator_streams_fmt,
            t.powerW.toInt(), t.hrBpm.toInt(), t.cadenceRpm.toInt(), t.speedMps * 3.6,
        )
        dashboardView.text = dashboard()
    }

    private fun clock(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

    private fun dashboard(): String {
        fun f(v: Float?, digits: Int = 1, unit: String = ""): String =
            if (v == null) "--" else "%.${digits}f%s".format(v, unit)
        fun row(label: String, value: String) = "%-22s%s\n".format(label, value)
        return buildString {
            append(row("Pw:Hr decoupling", f(engine.decoupling.value, 1, " %")))
            append(row("Pa:Hr decoupling", f(engine.paHrDecoupling.value, 1, " %")))
            append(row("Efficiency factor", f(engine.efficiencyFactor.value, 2)))
            append(row("Cardiac cost", f(engine.cardiacCost.value, 2, " bpm/W")))
            append(row("W' balance", f(engine.wPrimeBalance.value, 0, " J")))
            append(row("Cardiac pop", f(engine.cardiacPop.value, 0, " min")))
            append(row("AeT estimate", f(engine.aet.value, 0, " W")))
            append(row("Variability index", f(engine.variabilityIndex.value, 2)))
            append(row("Intensity factor", f(engine.intensityFactor.value, 2)))
            append(row("TSS", f(engine.tss.value, 1)))
            append(row("Kilojoules", f(engine.kilojoules.value, 0, " kJ")))
            append(row("Coasting", f(engine.coasting.value, 1, " %")))
            append(row("Quadrant", f(engine.quadrant.value, 0)))
            append(
                row(
                    "Best 5s/1m/5m",
                    listOf("mmp_5s", "mmp_1min", "mmp_5min")
                        .joinToString("/") { f(engine.mmpFlow(it).value, 0) } + " W",
                )
            )
            append(
                row(
                    "Best 20m/60m",
                    listOf("mmp_20min", "mmp_60min")
                        .joinToString("/") { f(engine.mmpFlow(it).value, 0) } + " W",
                )
            )
            append(row("VAM", f(engine.vam.value, 0, " m/h")))
            append(row("Optimal cadence", f(engine.optimalCadence.value, 0, " rpm")))
            append(row("RMSSD", f(strap.rmssd, 1, " ms")))
            append(row("HRV stress", f(strap.stressPct, 0, " %")))
            append(row("DFA α1", f(strap.dfaAlpha1, 2)))
            append(row("SDNN", f(strap.sdnn, 1, " ms")))
            append(row("pNN50", f(strap.pnn50, 1, " %")))
            append(row("SD1/SD2/ratio", "${f(strap.sd1, 1)}/${f(strap.sd2, 1)}/${f(strap.sd1Sd2Ratio, 2)}"))
            append(row("Resp rate", f(strap.respiratoryRate, 1, " brpm")))
            append(row("Irregular beats", f(strap.ectopicRate, 1, " /min")))
        }.trimEnd()
    }

    private companion object {
        val SPEEDS = intArrayOf(1, 10, 30, 60)
        const val DEFAULT_SPEED_INDEX = 2 // 30×
        const val UI_REFRESH_MS = 250L
    }
}
