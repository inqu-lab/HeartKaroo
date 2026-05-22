package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.aet.AerobicThresholdCalibrator
import com.inqulab.heartkaroo.cadence.OptimalCadenceCalculator
import com.inqulab.heartkaroo.climb.VamCalculator
import com.inqulab.heartkaroo.decoupling.CardiacPopDetector
import com.inqulab.heartkaroo.decoupling.DecouplingCalculator
import com.inqulab.heartkaroo.efficiency.CardiacCostCalculator
import com.inqulab.heartkaroo.efficiency.EfficiencyFactorCalculator
import com.inqulab.heartkaroo.hrv.DfaAlphaZoneTimer
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import com.inqulab.heartkaroo.wprime.WPrimeBalanceCalculator
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Single owner of every per-ride power / HR / cadence / climb metric.
 *
 * The calculators live here, fed by one long-lived set of stream collectors,
 * NOT inside each data field's startStream. A data field is only streamed while
 * its page is visible, and Karoo recreates the stream across lifecycle changes,
 * so a calculator owned by startStream is reset constantly — which means any
 * metric with a warmup (best 5-min power, Efficiency Factor, AeT, decoupling, …)
 * never fills and shows 0 / "Searching" forever, and cumulative ones (kJ, W′
 * balance) reset mid-ride. Here the metrics accumulate for the whole ride
 * regardless of what's on screen; each data field just collects the matching
 * StateFlow (null -> Searching, value -> Streaming). Reset per ride via
 * [resetRide].
 */
class RidePowerEngine(
    private val karooSystem: KarooSystemService,
    private val settings: RiderSettings,
    private val dfaAlpha1: Flow<Float?>,
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
    private val pwHrDecoupling = DecouplingCalculator()
    private val paHrDecouplingCalc = DecouplingCalculator()
    private val popDecoupling = DecouplingCalculator()
    private val popDetector = CardiacPopDetector()
    private val vamCalc = VamCalculator()
    private val aetCalc = AerobicThresholdCalibrator()
    private val cadenceCalc = OptimalCadenceCalculator()
    // FTP / CP / W′₀ feed split lines or the model at construction; rebuilt on
    // reset so a change in the Settings screen takes effect on the next ride.
    private var quadrantCalc = QuadrantAnalysisCalculator(ftpW = settings.ftpW.toDouble())
    private var wPrimeCalc = newWPrimeCalc()
    private val mmpCalcs = mmpDurations.mapValues { (_, d) -> MmpCalculator(d) }

    // After-ride summary state (snapshotted into the FIT session message).
    private val dfaZones = DfaAlphaZoneTimer()
    private var wPrimeMin = Double.POSITIVE_INFINITY
    private var matchesBurned = 0
    private var matchArmed = true

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
    private val _decoupling = MutableStateFlow<Float?>(null)
    val decoupling: StateFlow<Float?> = _decoupling.asStateFlow()
    private val _paHrDecoupling = MutableStateFlow<Float?>(null)
    val paHrDecoupling: StateFlow<Float?> = _paHrDecoupling.asStateFlow()
    private val _cardiacPop = MutableStateFlow<Float?>(null)
    val cardiacPop: StateFlow<Float?> = _cardiacPop.asStateFlow()
    private val _wPrimeBalance = MutableStateFlow<Float?>(null)
    val wPrimeBalance: StateFlow<Float?> = _wPrimeBalance.asStateFlow()
    private val _vam = MutableStateFlow<Float?>(null)
    val vam: StateFlow<Float?> = _vam.asStateFlow()
    private val _aet = MutableStateFlow<Float?>(null)
    val aet: StateFlow<Float?> = _aet.asStateFlow()
    private val _optimalCadence = MutableStateFlow<Float?>(null)
    val optimalCadence: StateFlow<Float?> = _optimalCadence.asStateFlow()
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
        scope.launch {
            karooSystem.streamDataFlow(DataType.Type.SPEED).collect { ss ->
                val s = ss.singleValue() ?: return@collect
                onSpeed(System.currentTimeMillis(), s)
            }
        }
        scope.launch {
            karooSystem.streamDataFlow(DataType.Type.ELEVATION_GAIN).collect { es ->
                val e = es.singleValue() ?: return@collect
                _vam.value = vamCalc.add(System.currentTimeMillis(), e)
            }
        }
        scope.launch {
            dfaAlpha1.filterNotNull().collect { a ->
                aetCalc.addAlpha(a)
                dfaZones.add(System.currentTimeMillis(), a.toDouble())
                _aet.value = aetCalc.currentEstimate()
            }
        }
    }

    @Synchronized
    private fun onPower(now: Long, power: Double) {
        val ftp = settings.ftpW.coerceAtLeast(1)
        val hr = latestHr

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
        val wBal = wPrimeCalc.add(now, power)
        _wPrimeBalance.value = wBal
        val wBalD = wBal.toDouble()
        if (wBalD < wPrimeMin) wPrimeMin = wBalD
        val wpJ = settings.wPrimeJ
        if (matchArmed && wBalD < wpJ * 0.25) {
            matchesBurned++
            matchArmed = false
        } else if (!matchArmed && wBalD >= wpJ * 0.30) {
            matchArmed = true
        }

        for ((id, calc) in mmpCalcs) _mmp.getValue(id).value = calc.add(now, power)

        if (hr != null && hr > 0.0) {
            _efficiencyFactor.value = efCalc.add(now, power, hr)
            _cardiacCost.value = ccCalc.add(now, power, hr)
            _decoupling.value = pwHrDecoupling.add(now, power, hr)?.toFloat()
            val popPct = popDecoupling.add(now, power, hr)
            popDetector.add(now, popPct)
        } else {
            _decoupling.value = pwHrDecoupling.current()?.toFloat()
            popDetector.add(now, popDecoupling.current())
        }
        _cardiacPop.value = popDetector.getPopMinutes()

        aetCalc.addPower(now, power)
        _aet.value = aetCalc.currentEstimate()

        val cad = latestCadence
        if (cad != null && cad > 0.0) {
            quadrantCalc.add(now, power, cad)
            if (hr != null && hr > 0.0) cadenceCalc.add(power, hr, cad)
        }
        _quadrant.value = quadrantCalc.dominantQuadrant()?.toFloat()
        _optimalCadence.value = cadenceCalc.optimalCadence()
    }

    @Synchronized
    private fun onSpeed(now: Long, speedMps: Double) {
        val hr = latestHr
        _paHrDecoupling.value = if (hr != null && hr > 0.0) {
            paHrDecouplingCalc.add(now, speedMps, hr)?.toFloat()
        } else {
            paHrDecouplingCalc.current()?.toFloat()
        }
    }

    /** AeT estimate / sample count for per-ride persistence (read at ride stop). */
    fun aetCurrentEstimate(): Float? = aetCalc.currentEstimate()
    val aetSampleCount: Int get() = aetCalc.sampleCount

    /** Optimal cadence / sample count for per-ride persistence (read at ride stop). */
    fun optimalCadenceCurrent(): Float? = cadenceCalc.optimalCadence()
    val optimalCadenceSamples: Int get() = cadenceCalc.totalSamples

    // After-ride summary snapshots for the FIT session writer.
    /** VT2 / second-threshold power from the AeT fit solved at DFA α1 = 0.50. */
    fun vt2CurrentEstimate(): Float? = aetCalc.estimateForTarget(0.50)
    @Synchronized fun wPrimeMinJ(): Double? = if (wPrimeMin.isFinite()) wPrimeMin else null
    val matchesBurnedCount: Int @Synchronized get() = matchesBurned
    fun dfaAerobicSeconds(): Double = dfaZones.aerobicSeconds()
    fun dfaThresholdSeconds(): Double = dfaZones.thresholdSeconds()
    fun dfaHardSeconds(): Double = dfaZones.hardSeconds()
    fun quadrantDistribution(): DoubleArray? = quadrantCalc.distributionPercent()

    /** Reset every per-ride accumulator. Call when a new ride starts. */
    @Synchronized
    fun resetRide() {
        npCalc.reset()
        npTss.reset()
        kjCalc.reset()
        coastingCalc.reset()
        efCalc.reset()
        ccCalc.reset()
        pwHrDecoupling.reset()
        paHrDecouplingCalc.reset()
        popDecoupling.reset()
        popDetector.reset()
        vamCalc.reset()
        aetCalc.reset()
        cadenceCalc.reset()
        quadrantCalc = QuadrantAnalysisCalculator(ftpW = settings.ftpW.toDouble())
        wPrimeCalc = newWPrimeCalc()
        mmpCalcs.values.forEach { it.reset() }
        dfaZones.reset()
        wPrimeMin = Double.POSITIVE_INFINITY
        matchesBurned = 0
        matchArmed = true

        _intensityFactor.value = null
        _variabilityIndex.value = null
        _tss.value = null
        _kilojoules.value = null
        _coasting.value = null
        _quadrant.value = null
        _efficiencyFactor.value = null
        _cardiacCost.value = null
        _decoupling.value = null
        _paHrDecoupling.value = null
        _cardiacPop.value = null
        _wPrimeBalance.value = null
        _vam.value = null
        _aet.value = null
        _optimalCadence.value = null
        _mmp.values.forEach { it.value = null }
    }

    private fun newWPrimeCalc() = WPrimeBalanceCalculator(
        criticalPowerW = settings.criticalPowerW.toDouble(),
        wPrimeJ = settings.wPrimeJ.toDouble(),
    )

    private fun StreamState.singleValue(): Double? =
        (this as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
}
