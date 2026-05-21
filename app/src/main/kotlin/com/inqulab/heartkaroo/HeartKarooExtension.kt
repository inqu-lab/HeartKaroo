package com.inqulab.heartkaroo

import com.inqulab.heartkaroo.aet.AerobicThresholdCalibrator
import com.inqulab.heartkaroo.aet.AerobicThresholdDataType
import com.inqulab.heartkaroo.aet.AerobicThresholdStore
import com.inqulab.heartkaroo.cadence.OptimalCadenceCalculator
import com.inqulab.heartkaroo.cadence.OptimalCadenceDataType
import com.inqulab.heartkaroo.cadence.OptimalCadenceStore
import com.inqulab.heartkaroo.climb.VamDataType
import com.inqulab.heartkaroo.decoupling.CardiacPopDataType
import com.inqulab.heartkaroo.decoupling.CardiacPopDetector
import com.inqulab.heartkaroo.decoupling.DecouplingCalculator
import com.inqulab.heartkaroo.decoupling.DecouplingDataType
import com.inqulab.heartkaroo.decoupling.PaHrDecouplingDataType
import com.inqulab.heartkaroo.efficiency.CardiacCostDataType
import com.inqulab.heartkaroo.efficiency.EfficiencyFactorDataType
import com.inqulab.heartkaroo.power.CoastingDataType
import com.inqulab.heartkaroo.power.IntensityFactorDataType
import com.inqulab.heartkaroo.power.KilojoulesDataType
import com.inqulab.heartkaroo.power.MmpDataType
import com.inqulab.heartkaroo.power.QuadrantAnalysisCalculator
import com.inqulab.heartkaroo.power.QuadrantAnalysisDataType
import com.inqulab.heartkaroo.power.TssDataType
import com.inqulab.heartkaroo.power.VariabilityIndexDataType
import com.inqulab.heartkaroo.hrv.DfaAlpha1DataType
import com.inqulab.heartkaroo.hrv.DfaAlphaZoneTimer
import com.inqulab.heartkaroo.hrv.HRVDataType
import com.inqulab.heartkaroo.hrv.HRVStressDataType
import com.inqulab.heartkaroo.hrv.HrvFlowDataType
import com.inqulab.heartkaroo.hrv.PolarBleManager
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import com.inqulab.heartkaroo.wprime.WPrimeBalanceCalculator
import com.inqulab.heartkaroo.wprime.WPrimeBalanceDataType
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class HeartKarooExtension : KarooExtension(EXTENSION_ID, "1.0.0") {

    companion object {
        const val EXTENSION_ID = "heartkaroo"

        val RMSSD_FIELD = DeveloperField(
            fieldDefinitionNumber = 0,
            fitBaseTypeId = 136,
            fieldName = "hrv_rmssd",
            units = "ms",
        )

        val STRESS_FIELD = DeveloperField(
            fieldDefinitionNumber = 1,
            fitBaseTypeId = 136,
            fieldName = "hrv_stress_pct",
            units = "pct",
        )

        val DFA_ALPHA1_FIELD = DeveloperField(
            fieldDefinitionNumber = 2,
            fitBaseTypeId = 136,
            // intervals.icu recognises DFA a1 only when the developer field is
            // named "Alpha1" (the alphaHRV Connect IQ convention) — it then
            // computes Average DFA a1 from this 1Hz stream itself.
            fieldName = "Alpha1",
            units = "",
        )

        val RESPIRATORY_RATE_FIELD = DeveloperField(
            fieldDefinitionNumber = 3,
            fitBaseTypeId = 136,
            fieldName = "respiratory_rate",
            units = "brpm",
            // Map onto the native FIT record respiration_rate field (108) so
            // readers (intervals.icu, Garmin Connect) treat it as real
            // respiration rather than an opaque custom stream.
            nativeFieldNum = 108,
        )

        val SDNN_FIELD = DeveloperField(
            fieldDefinitionNumber = 4,
            fitBaseTypeId = 136,
            fieldName = "sdnn",
            units = "ms",
        )

        val AET_FIELD = DeveloperField(
            fieldDefinitionNumber = 5,
            fitBaseTypeId = 136,
            fieldName = "aet_estimate",
            units = "watts",
        )

        // After-ride summary fields written to the session message so they
        // surface as per-ride numbers (custom activity fields in intervals.icu).
        val OPTIMAL_CADENCE_FIELD = DeveloperField(
            fieldDefinitionNumber = 6,
            fitBaseTypeId = 136,
            fieldName = "optimal_cadence",
            units = "rpm",
        )

        val WPRIME_MIN_FIELD = DeveloperField(
            fieldDefinitionNumber = 7,
            fitBaseTypeId = 136,
            fieldName = "w_prime_min",
            units = "J",
        )

        val VT2_FIELD = DeveloperField(
            fieldDefinitionNumber = 8,
            fitBaseTypeId = 136,
            fieldName = "vt2_estimate",
            units = "watts",
        )

        val DFA_A1_AEROBIC_FIELD = DeveloperField(
            fieldDefinitionNumber = 9,
            fitBaseTypeId = 136,
            fieldName = "dfa_a1_aerobic_s",
            units = "s",
        )

        val DFA_A1_THRESHOLD_FIELD = DeveloperField(
            fieldDefinitionNumber = 10,
            fitBaseTypeId = 136,
            fieldName = "dfa_a1_threshold_s",
            units = "s",
        )

        val DFA_A1_HARD_FIELD = DeveloperField(
            fieldDefinitionNumber = 11,
            fitBaseTypeId = 136,
            fieldName = "dfa_a1_hard_s",
            units = "s",
        )

        val CARDIAC_POP_FIELD = DeveloperField(
            fieldDefinitionNumber = 12,
            fitBaseTypeId = 136,
            fieldName = "cardiac_pop_min",
            units = "min",
        )

        val MATCHES_BURNED_FIELD = DeveloperField(
            fieldDefinitionNumber = 13,
            fitBaseTypeId = 136,
            fieldName = "matches_burned",
            units = "",
        )

        val QUADRANT1_FIELD = DeveloperField(
            fieldDefinitionNumber = 14,
            fitBaseTypeId = 136,
            fieldName = "quadrant1_pct",
            units = "pct",
        )

        val QUADRANT2_FIELD = DeveloperField(
            fieldDefinitionNumber = 15,
            fitBaseTypeId = 136,
            fieldName = "quadrant2_pct",
            units = "pct",
        )

        val QUADRANT3_FIELD = DeveloperField(
            fieldDefinitionNumber = 16,
            fitBaseTypeId = 136,
            fieldName = "quadrant3_pct",
            units = "pct",
        )

        val QUADRANT4_FIELD = DeveloperField(
            fieldDefinitionNumber = 17,
            fitBaseTypeId = 136,
            fieldName = "quadrant4_pct",
            units = "pct",
        )

        private const val MIN_AET_SAMPLES_TO_PERSIST = 60
        private const val MIN_CADENCE_SAMPLES_TO_PERSIST = 300

        // How often the after-ride session summaries are (re)written; the
        // last write before the ride ends supplies the final values.
        private const val SESSION_SUMMARY_INTERVAL_MS = 15_000L

        // Warn at/below this strap battery %, re-arming once it recovers above
        // the second threshold (so we don't alert repeatedly around the line).
        private const val LOW_BATTERY_PCT = 15
        private const val BATTERY_RECOVERED_PCT = 25
    }

    lateinit var karooSystem: KarooSystemService
        private set

    lateinit var bleManager: PolarBleManager
        private set

    override val types by lazy {
        listOf(
            DecouplingDataType(this),
            PaHrDecouplingDataType(this),
            EfficiencyFactorDataType(this),
            CardiacCostDataType(this),
            WPrimeBalanceDataType(this),
            CardiacPopDataType(this),
            AerobicThresholdDataType(this),
            OptimalCadenceDataType(this),
            VariabilityIndexDataType(this),
            IntensityFactorDataType(this),
            TssDataType(this),
            KilojoulesDataType(this),
            CoastingDataType(this),
            QuadrantAnalysisDataType(this),
            VamDataType(this),
            MmpDataType(this, 5_000L, "mmp_5s"),
            MmpDataType(this, 60_000L, "mmp_1min"),
            MmpDataType(this, 5L * 60 * 1000, "mmp_5min"),
            MmpDataType(this, 20L * 60 * 1000, "mmp_20min"),
            MmpDataType(this, 60L * 60 * 1000, "mmp_60min"),
            HRVDataType(bleManager, EXTENSION_ID),
            HRVStressDataType(bleManager, EXTENSION_ID),
            DfaAlpha1DataType(bleManager, EXTENSION_ID),
            HrvFlowDataType(EXTENSION_ID, "hrv_sdnn", bleManager.sdnnFlow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_pnn50", bleManager.pnn50Flow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd1", bleManager.sd1Flow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd2", bleManager.sd2Flow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd1_sd2_ratio", bleManager.sd1Sd2RatioFlow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "respiratory_rate", bleManager.respiratoryRateFlow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "ectopic_rate", bleManager.ectopicRateFlow, bleManager.connectedFlow),
        )
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        bleManager = PolarBleManager(applicationContext)
        karooSystem.connect {}
        watchStrapBattery()
    }

    /** Raises a single in-ride alert when the strap battery first drops to the
     *  warning level, re-arming only once it has recovered (fresh battery). */
    private fun watchStrapBattery() {
        serviceScope.launch {
            var warned = false
            bleManager.batteryFlow.filterNotNull().collect { level ->
                if (level <= LOW_BATTERY_PCT && !warned) {
                    warned = true
                    karooSystem.dispatch(
                        InRideAlert(
                            id = "heartkaroo-strap-battery",
                            icon = R.drawable.ic_hrv,
                            title = "Strap battery low",
                            detail = "Polar strap at $level%",
                            autoDismissMs = 15_000L,
                            backgroundColor = android.R.color.holo_red_dark,
                            textColor = android.R.color.white,
                        )
                    )
                } else if (level > BATTERY_RECOVERED_PCT) {
                    warned = false
                }
            }
        }
    }

    override fun startScan(emitter: Emitter<Device>) {
        val stop = bleManager.startDeviceScan { device ->
            emitter.onNext(
                Device(
                    extension = EXTENSION_ID,
                    uid = device.id,
                    dataTypes = listOf(DataType.Type.HEART_RATE),
                    // Suffix so this entry is distinguishable from Karoo's own
                    // native HR pairing of the same strap. Pair THIS one only —
                    // it provides both HR and HRV from the single H10 connection
                    // (the strap allows only one BLE link at a time).
                    displayName = "${device.name} (HR+HRV)",
                )
            )
        }
        emitter.setCancellable { stop() }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
        val job: Job = CoroutineScope(Dispatchers.IO).launch {
            bleManager.connect(uid).collect { event ->
                when (event) {
                    is PolarBleManager.BleEvent.Connected ->
                        emitter.onNext(OnConnectionStatus(ConnectionStatus.CONNECTED))
                    is PolarBleManager.BleEvent.Disconnected ->
                        emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
                    is PolarBleManager.BleEvent.Heartrate ->
                        emitter.onNext(
                            OnDataPoint(
                                DataPoint(
                                    dataTypeId = DataType.Type.HEART_RATE,
                                    values = mapOf(DataType.Field.SINGLE to event.bpm.toDouble()),
                                )
                            )
                        )
                }
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        val scope = CoroutineScope(Dispatchers.IO)
        val rmssdJob: Job = scope.launch {
            bleManager.rmssdFlow
                .filter { it > 0f }
                .collect { rmssd ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(RMSSD_FIELD, rmssd.toDouble())))
                }
        }
        val stressJob: Job = scope.launch {
            bleManager.stressFlow
                .filterNotNull()
                .collect { stress ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(STRESS_FIELD, stress.toDouble())))
                }
        }
        val dfaJob: Job = scope.launch {
            bleManager.dfaAlpha1Flow
                .filterNotNull()
                .collect { alpha ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(DFA_ALPHA1_FIELD, alpha.toDouble())))
                }
        }
        val respJob: Job = scope.launch {
            bleManager.respiratoryRateFlow
                .filterNotNull()
                .collect { brpm ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(RESPIRATORY_RATE_FIELD, brpm.toDouble())))
                }
        }
        val sdnnJob: Job = scope.launch {
            bleManager.sdnnFlow
                .filterNotNull()
                .collect { sdnn ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(SDNN_FIELD, sdnn.toDouble())))
                }
        }
        val settings = RiderSettings(applicationContext)
        val aetCalibrator = AerobicThresholdCalibrator()
        val aetPowerJob: Job = scope.launch {
            karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                aetCalibrator.addPower(System.currentTimeMillis(), p)
            }
        }
        val dfaZones = DfaAlphaZoneTimer()
        val aetAlphaJob: Job = scope.launch {
            bleManager.dfaAlpha1Flow.filterNotNull().collect { a ->
                aetCalibrator.addAlpha(a)
                dfaZones.add(System.currentTimeMillis(), a.toDouble())
                aetCalibrator.currentEstimate()?.let { est ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(AET_FIELD, est.toDouble())))
                }
            }
        }
        val cadenceCalc = OptimalCadenceCalculator()
        val quadrantCalc = QuadrantAnalysisCalculator(ftpW = settings.ftpW.toDouble())
        val cadenceJob: Job = scope.launch {
            combine(
                karooSystem.streamDataFlow(DataType.Type.POWER),
                karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
                karooSystem.streamDataFlow(DataType.Type.CADENCE),
            ) { ps, hs, cs ->
                Triple(
                    (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (hs as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (cs as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                )
            }.collect { (p, h, c) ->
                val now = System.currentTimeMillis()
                if (p != null && c != null) quadrantCalc.add(now, p, c)
                if (p != null && h != null && c != null) cadenceCalc.add(p, h, c)
            }
        }
        val wPrimeCalc = WPrimeBalanceCalculator(
            criticalPowerW = settings.criticalPowerW.toDouble(),
            wPrimeJ = settings.wPrimeJ.toDouble(),
        )
        // A "match" is each fresh dip below 25% W′, re-armed only once it has
        // recovered above 30% so flicker around the line doesn't double-count.
        val matchThreshold = settings.wPrimeJ * 0.25
        val matchRearm = settings.wPrimeJ * 0.30
        var wPrimeMin = Double.POSITIVE_INFINITY
        var matchesBurned = 0
        var matchArmed = true
        val wPrimeJob: Job = scope.launch {
            karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                val bal = wPrimeCalc.add(System.currentTimeMillis(), p).toDouble()
                if (bal < wPrimeMin) wPrimeMin = bal
                if (matchArmed && bal < matchThreshold) {
                    matchesBurned++
                    matchArmed = false
                } else if (!matchArmed && bal >= matchRearm) {
                    matchArmed = true
                }
            }
        }
        val decouplingCalc = DecouplingCalculator()
        val popDetector = CardiacPopDetector()
        val cardiacPopJob: Job = scope.launch {
            combine(
                karooSystem.streamDataFlow(DataType.Type.POWER),
                karooSystem.streamDataFlow(DataType.Type.HEART_RATE),
            ) { ps, hs ->
                Pair(
                    (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                    (hs as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull(),
                )
            }.collect { (p, h) ->
                val now = System.currentTimeMillis()
                val pct = if (p != null && h != null) decouplingCalc.add(now, p, h)
                else decouplingCalc.current()
                popDetector.add(now, pct)
            }
        }
        // Single periodic writer for the after-ride session summaries.
        // WriteToSessionMesg keeps the latest value, so the final tick before
        // the ride ends carries the ride's summary numbers.
        val summaryJob: Job = scope.launch {
            while (isActive) {
                delay(SESSION_SUMMARY_INTERVAL_MS)
                aetCalibrator.currentEstimate()?.let {
                    emitter.onNext(WriteToSessionMesg(FieldValue(AET_FIELD, it.toDouble())))
                }
                aetCalibrator.estimateForTarget(0.50)?.let {
                    emitter.onNext(WriteToSessionMesg(FieldValue(VT2_FIELD, it.toDouble())))
                }
                cadenceCalc.optimalCadence()?.let {
                    emitter.onNext(WriteToSessionMesg(FieldValue(OPTIMAL_CADENCE_FIELD, it.toDouble())))
                }
                if (wPrimeMin.isFinite()) {
                    emitter.onNext(WriteToSessionMesg(FieldValue(WPRIME_MIN_FIELD, wPrimeMin)))
                    emitter.onNext(WriteToSessionMesg(FieldValue(MATCHES_BURNED_FIELD, matchesBurned.toDouble())))
                }
                emitter.onNext(WriteToSessionMesg(FieldValue(DFA_A1_AEROBIC_FIELD, dfaZones.aerobicSeconds())))
                emitter.onNext(WriteToSessionMesg(FieldValue(DFA_A1_THRESHOLD_FIELD, dfaZones.thresholdSeconds())))
                emitter.onNext(WriteToSessionMesg(FieldValue(DFA_A1_HARD_FIELD, dfaZones.hardSeconds())))
                popDetector.getPopMinutes()?.let {
                    emitter.onNext(WriteToSessionMesg(FieldValue(CARDIAC_POP_FIELD, it.toDouble())))
                }
                quadrantCalc.distributionPercent()?.let { d ->
                    emitter.onNext(WriteToSessionMesg(FieldValue(QUADRANT1_FIELD, d[0])))
                    emitter.onNext(WriteToSessionMesg(FieldValue(QUADRANT2_FIELD, d[1])))
                    emitter.onNext(WriteToSessionMesg(FieldValue(QUADRANT3_FIELD, d[2])))
                    emitter.onNext(WriteToSessionMesg(FieldValue(QUADRANT4_FIELD, d[3])))
                }
            }
        }
        emitter.setCancellable {
            rmssdJob.cancel()
            stressJob.cancel()
            dfaJob.cancel()
            respJob.cancel()
            sdnnJob.cancel()
            aetPowerJob.cancel()
            aetAlphaJob.cancel()
            cadenceJob.cancel()
            wPrimeJob.cancel()
            cardiacPopJob.cancel()
            summaryJob.cancel()
            val final = aetCalibrator.currentEstimate()
            val samples = aetCalibrator.sampleCount
            if (final != null && samples >= MIN_AET_SAMPLES_TO_PERSIST) {
                AerobicThresholdStore(applicationContext)
                    .record(System.currentTimeMillis(), final, samples)
            }
            val cadenceFinal = cadenceCalc.optimalCadence()
            val cadenceSamples = cadenceCalc.totalSamples
            if (cadenceFinal != null && cadenceSamples >= MIN_CADENCE_SAMPLES_TO_PERSIST) {
                OptimalCadenceStore(applicationContext)
                    .record(System.currentTimeMillis(), cadenceFinal, cadenceSamples)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }
}
