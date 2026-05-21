package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.efficiency.CardiacCostCalculator
import com.inqulab.heartkaroo.efficiency.EfficiencyFactorCalculator
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Single owner of every per-ride power / HR / cadence metric.
 *
 * The calculators live here, fed by one long-lived set of stream collectors,
 * NOT inside each data field's startStream. A data field is only streamed while
 * its page is visible, and Karoo recreates the stream across lifecycle changes,
 * so a calculator owned by startStream is reset constantly — which means any
 * metric with a warmup (best 5-min power, Efficiency Factor, …) never fills and
 * shows 0 / "Searching" forever. Here the metrics accumulate for the whole ride
 * regardless of what's on screen; each data field just collects the matching
 * StateFlow (null -> Searching, value -> Streaming). Reset per ride via
 * [resetRide].
 */
class RidePowerEngine(
    private val karooSystem: KarooSystemService,
    private val settings: RiderSettings,
    private val scope: CoroutineScope,
) {
    private val mmpDurations = linkedMapOf(
        "mmp_5s" to 5_000L,
        "mmp_1min" to 60_000L,
        "mmp_5min" to 5L * 60 * 1000,
        "mmp_20min" to 20L * 60 * 1000,
        "mmp_60min" to 60L * 60 * 1000,
    )

    // IF and VI share a 60-min Normalized Power window; TSS is cumulative (4 h).
    private val npCalc = NormalizedPowerCalculator()
    private val npTss = NormalizedPowerCalculator(windowMs = 4 * 60 * 60 * 1000L)
    private val kjCalc = KilojoulesCalculator()
    private val coastingCalc = CoastingCalculator()
    private val efCalc = EfficiencyFactorCalculator()
    private val ccCalc = CardiacCostCalculator()
    // FTP only feeds the quadrant split lines at construction; rebuilt on reset.
    private var quadrantCalc = QuadrantAnalysisCalculator(ftpW = settings.ftpW.toDouble())
    private val mmpCalcs = mmpDurations.mapValues { (_, d) -> MmpCalculator(d) }

    private val _intensityFactor = MutableStateFlow<Float?>(null)
    val intensityFactor: StateFlow<Float?> = _intensityFactor.asStateFlow()
    private val _variabilityIndex = MutableStateFlow<Float?>(null)
    val variabilityIndex: StateFlow<Float?> = _variabilityIndex.asStateFlow()
    private val _tss = MutableStateFlow<Float?>(null)
    val tss: StateFlow<Float?> = _tss.asStateFlow()
    private val _kilojoules = MutableStateFlow<Float?>(null)
    val kilojoules: StateFlow<Float?> = _kilojoules.asStateFlow()
    private val _coasting = MutableStateFlow<Float?>(null)
    val coasting: StateFlow<Float?> = _coasting.asStateFlow()
    private val _quadrant = MutableStateFlow<Float?>(null)
    val quadrant: StateFlow<Float?> = _quadrant.asStateFlow()
    private val _efficiencyFactor = MutableStateFlow<Float?>(null)
    val efficiencyFactor: StateFlow<Float?> = _efficiencyFactor.asStateFlow()
    private val _cardiacCost = MutableStateFlow<Float?>(null)
    val cardiacCost: StateFlow<Float?> = _cardiacCost.asStateFlow()
    private val _mmp = mmpDurations.keys.associateWith { MutableStateFlow<Float?>(null) }

    fun mmpFlow(typeId: String): StateFlow<Float?> = _mmp.getValue(typeId).asStateFlow()

    @Volatile
    private var latestHr: Double? = null

    @Volatile
    private var latestCadence: Double? = null

    /** Start the long-lived collectors. Call once from the service. Consumers
     *  registered before the KarooSystem connects are queued and reapplied. */
    fun start() {
        scope.launch {
            karooSystem.streamDataFlow(DataType.Type.HEART_RATE)
                .collect { latestHr = it.singleValue() }
        }
        scope.launch {
            karooSystem.streamDataFlow(DataType.Type.CADENCE)
                .collect { latestCadence = it.singleValue() }
        }
        scope.launch {
            karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = ps.singleValue() ?: return@collect
                onPower(System.currentTimeMillis(), p)
            }
        }
    }

    @Synchronized
    private fun onPower(now: Long, power: Double) {
        val ftp = settings.ftpW.coerceAtLeast(1)

        npCalc.add(now, power)
        val np = npCalc.normalizedPower()
        _intensityFactor.value = np?.let { it / ftp }
        val avg = npCalc.averagePower()
        _variabilityIndex.value = if (np != null && avg != null && avg > 0f) np / avg else null

        npTss.add(now, power)
        val npT = npTss.normalizedPower()
        _tss.value = if (npT == null) {
            null
        } else {
            val hours = npTss.elapsedSec() / 3600.0
            val iF = npT / ftp
            (hours * iF * iF * 100.0).toFloat()
        }

        _kilojoules.value = kjCalc.add(now, power)
        _coasting.value = coastingCalc.add(now, power)

        for ((id, calc) in mmpCalcs) _mmp.getValue(id).value = calc.add(now, power)

        val hr = latestHr
        if (hr != null && hr > 0.0) {
            _efficiencyFactor.value = efCalc.add(now, power, hr)
            _cardiacCost.value = ccCalc.add(now, power, hr)
        }

        val cad = latestCadence
        if (cad != null && cad > 0.0) quadrantCalc.add(now, power, cad)
        _quadrant.value = quadrantCalc.dominantQuadrant()?.toFloat()
    }

    /** Reset every per-ride accumulator. Call when a new ride starts. */
    @Synchronized
    fun resetRide() {
        npCalc.reset()
        npTss.reset()
        kjCalc.reset()
        coastingCalc.reset()
        efCalc.reset()
        ccCalc.reset()
        quadrantCalc = QuadrantAnalysisCalculator(ftpW = settings.ftpW.toDouble())
        mmpCalcs.values.forEach { it.reset() }

        _intensityFactor.value = null
        _variabilityIndex.value = null
        _tss.value = null
        _kilojoules.value = null
        _coasting.value = null
        _quadrant.value = null
        _efficiencyFactor.value = null
        _cardiacCost.value = null
        _mmp.values.forEach { it.value = null }
    }

    private fun StreamState.singleValue(): Double? =
        (this as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
}
