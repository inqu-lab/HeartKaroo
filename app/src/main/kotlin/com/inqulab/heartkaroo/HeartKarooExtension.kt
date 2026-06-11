package com.inqulab.heartkaroo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.inqulab.heartkaroo.aet.AerobicThresholdDataType
import com.inqulab.heartkaroo.aet.AerobicThresholdStore
import com.inqulab.heartkaroo.cadence.OptimalCadenceDataType
import com.inqulab.heartkaroo.cadence.OptimalCadenceStore
import com.inqulab.heartkaroo.climb.VamDataType
import com.inqulab.heartkaroo.decoupling.CardiacPopDataType
import com.inqulab.heartkaroo.decoupling.DecouplingDataType
import com.inqulab.heartkaroo.decoupling.PaHrDecouplingDataType
import com.inqulab.heartkaroo.efficiency.CardiacCostDataType
import com.inqulab.heartkaroo.efficiency.EfficiencyFactorDataType
import com.inqulab.heartkaroo.power.CoastingDataType
import com.inqulab.heartkaroo.power.EftpDataType
import com.inqulab.heartkaroo.power.IntensityFactorDataType
import com.inqulab.heartkaroo.power.KilojoulesDataType
import com.inqulab.heartkaroo.power.MmpDataType
import com.inqulab.heartkaroo.power.QuadrantAnalysisDataType
import com.inqulab.heartkaroo.power.RidePowerEngine
import com.inqulab.heartkaroo.power.TssDataType
import com.inqulab.heartkaroo.power.VariabilityIndexDataType
import com.inqulab.heartkaroo.power.WattsPerKgDataType
import com.inqulab.heartkaroo.settings.RiderSettings
import com.inqulab.heartkaroo.hrv.DfaAlpha1DataType
import com.inqulab.heartkaroo.hrv.HRVDataType
import com.inqulab.heartkaroo.hrv.HRVStressDataType
import com.inqulab.heartkaroo.hrv.HrvFlowDataType
import com.inqulab.heartkaroo.hrv.PolarBleManager
import com.inqulab.heartkaroo.wprime.WPrimeBalanceDataType
import com.inqulab.heartkaroo.wprime.WPrimePercentDataType
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
import io.hammerhead.karooext.models.RequestBluetooth
import io.hammerhead.karooext.models.SystemNotification
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.models.WriteToSessionMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class HeartKarooExtension : KarooExtension(EXTENSION_ID, "1.0.0") {

    /** A read of the engine's after-ride summary metrics at one tick. */
    class RideSummarySnapshot(
        val aet: Float?,
        val vt2: Float?,
        val optimalCadence: Float?,
        val wPrimeMinJ: Double?,
        val matchesBurned: Int,
        val dfaAerobicS: Double,
        val dfaThresholdS: Double,
        val dfaHardS: Double,
        val cardiacPopMin: Float?,
        val quadrantPct: DoubleArray?,
    )

    companion object {
        const val EXTENSION_ID = "heartkaroo"

        // uid of the always-present virtual sensor entry (listed without a BLE
        // scan so it's pairable before the Nearby-devices permission is granted).
        const val VIRTUAL_HR_UID = "heartkaroo-hr"

        // Reserve the BT radio once per process. Re-requesting on every service
        // recreate (e.g. at ride start) risks cycling the radio and dropping the
        // strap, and we never release it — the reservation lives with the process.
        @Volatile
        private var bluetoothRequested = false

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

        /**
         * Field values for the session message from one engine snapshot.
         * Metrics that haven't resolved (null) are omitted; matches-burned only
         * accompanies W′-min (no W′ data means the count is meaningless); the DFA
         * α1 zone seconds are always written (0 is a valid "no time there").
         */
        fun sessionSummaryFields(s: RideSummarySnapshot): List<FieldValue> = buildList {
            s.aet?.let { add(FieldValue(AET_FIELD, it.toDouble())) }
            s.vt2?.let { add(FieldValue(VT2_FIELD, it.toDouble())) }
            s.optimalCadence?.let { add(FieldValue(OPTIMAL_CADENCE_FIELD, it.toDouble())) }
            s.wPrimeMinJ?.let {
                add(FieldValue(WPRIME_MIN_FIELD, it))
                add(FieldValue(MATCHES_BURNED_FIELD, s.matchesBurned.toDouble()))
            }
            add(FieldValue(DFA_A1_AEROBIC_FIELD, s.dfaAerobicS))
            add(FieldValue(DFA_A1_THRESHOLD_FIELD, s.dfaThresholdS))
            add(FieldValue(DFA_A1_HARD_FIELD, s.dfaHardS))
            s.cardiacPopMin?.let { add(FieldValue(CARDIAC_POP_FIELD, it.toDouble())) }
            s.quadrantPct?.let { d ->
                add(FieldValue(QUADRANT1_FIELD, d[0]))
                add(FieldValue(QUADRANT2_FIELD, d[1]))
                add(FieldValue(QUADRANT3_FIELD, d[2]))
                add(FieldValue(QUADRANT4_FIELD, d[3]))
            }
        }

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

    lateinit var ridePowerEngine: RidePowerEngine
        private set

    override val types by lazy {
        listOf(
            DecouplingDataType(this),
            PaHrDecouplingDataType(this),
            EfficiencyFactorDataType(this),
            CardiacCostDataType(this),
            WPrimeBalanceDataType(this),
            WPrimePercentDataType(this),
            CardiacPopDataType(this),
            AerobicThresholdDataType(this),
            OptimalCadenceDataType(this),
            VariabilityIndexDataType(this),
            IntensityFactorDataType(this),
            TssDataType(this),
            KilojoulesDataType(this),
            CoastingDataType(this),
            QuadrantAnalysisDataType(this),
            WattsPerKgDataType(this),
            EftpDataType(this),
            VamDataType(this),
            MmpDataType(this, "mmp_5s"),
            MmpDataType(this, "mmp_1min"),
            MmpDataType(this, "mmp_5min"),
            MmpDataType(this, "mmp_20min"),
            MmpDataType(this, "mmp_60min"),
            HRVDataType(bleManager, EXTENSION_ID),
            HRVStressDataType(bleManager, EXTENSION_ID),
            DfaAlpha1DataType(bleManager, EXTENSION_ID),
            HrvFlowDataType(EXTENSION_ID, "hrv_sdnn", bleManager.sdnnFlow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_pnn50", bleManager.pnn50Flow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd1", bleManager.sd1Flow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd2", bleManager.sd2Flow, bleManager.connectedFlow),
            HrvFlowDataType(
                EXTENSION_ID, "hrv_sd1_sd2_ratio", bleManager.sd1Sd2RatioFlow,
                bleManager.connectedFlow, DataType.Type.INTENSITY_FACTOR,
            ),
            HrvFlowDataType(EXTENSION_ID, "respiratory_rate", bleManager.respiratoryRateFlow, bleManager.connectedFlow),
            HrvFlowDataType(EXTENSION_ID, "ectopic_rate", bleManager.ectopicRateFlow, bleManager.connectedFlow),
        )
    }

    // SupervisorJob so one failing collector (engine stream, battery watch)
    // doesn't cancel the others.
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        bleManager = PolarBleManager.getInstance(applicationContext)
        // Owns the per-ride power metrics and feeds them from one long-lived set
        // of stream collectors, so they accumulate for the whole ride regardless
        // of which page is on screen (see RidePowerEngine).
        ridePowerEngine = RidePowerEngine(
            karooSystem, RiderSettings(applicationContext), bleManager.dfaAlpha1Flow, serviceScope,
        )
        // We run our own BLE stack (Polar SDK) for the strap. Tell Karoo we're
        // using the radio so the system coordinates with us instead of reclaiming
        // it when a ride starts. Requested once per process (see flag) and never
        // released, so a service recreate doesn't cycle the radio.
        karooSystem.connect { connected ->
            if (connected && !bluetoothRequested) {
                bluetoothRequested = true
                karooSystem.dispatch(RequestBluetooth(EXTENSION_ID))
            }
            // The Sensors-section scan runs in this Service, which can't prompt for
            // runtime permissions. If they're missing, nudge the rider to open the
            // app (MainActivity requests them) so the strap becomes findable.
            if (connected && !hasBlePermissions()) {
                karooSystem.dispatch(
                    SystemNotification(
                        "heartkaroo-ble-permission",
                        getString(R.string.ble_permission_notification),
                        action = getString(R.string.ble_permission_notification_action),
                        actionIntent = "com.inqulab.heartkaroo.MAIN",
                    ),
                )
            }
        }
        ridePowerEngine.start()
        watchStrapBattery()
    }

    private fun hasBlePermissions(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            granted(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    /** Raises a single in-ride alert when the strap battery first drops to the
     *  warning level, re-arming only once it has recovered (fresh battery). */
    private fun watchStrapBattery() {
        serviceScope.launch {
            val alerter = StrapBatteryAlerter(LOW_BATTERY_PCT, BATTERY_RECOVERED_PCT)
            bleManager.batteryFlow.filterNotNull().collect { level ->
                if (alerter.shouldAlert(level)) {
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
                }
            }
        }
    }

    override fun startScan(emitter: Emitter<Device>) {
        // Always list a virtual entry first, so the strap is pairable even before
        // the Nearby-devices permission is granted — a real BLE scan needs that
        // permission and this Service can't prompt for it, which otherwise leaves
        // Karoo's Sensors list empty. connectDevice resolves the virtual entry to a
        // real strap. (Same trick as the veloVigil extension.)
        emitter.onNext(
            Device(
                extension = EXTENSION_ID,
                uid = VIRTUAL_HR_UID,
                dataTypes = listOf(DataType.Type.HEART_RATE),
                displayName = "HeartKaroo (HR+HRV)",
            )
        )
        // Also run a real scan so, once permission is granted, the rider can pick a
        // specific strap by name. With no permission this simply finds nothing.
        val stop = runCatching {
            bleManager.startDeviceScan { device ->
                emitter.onNext(
                    Device(
                        extension = EXTENSION_ID,
                        uid = device.id,
                        dataTypes = listOf(DataType.Type.HEART_RATE),
                        // Suffix so this entry is distinguishable from Karoo's own
                        // native HR pairing of the same strap. Pair a HeartKaroo
                        // entry — it provides both HR and HRV from one H10 link.
                        displayName = "${device.name} (HR+HRV)",
                    )
                )
            }
        }.getOrNull()
        emitter.setCancellable { stop?.invoke() }
    }

    override fun connectDevice(uid: String, emitter: Emitter<DeviceEvent>) {
        emitter.onNext(OnConnectionStatus(ConnectionStatus.SEARCHING))
        // The link is owned by the manager, so cancelling this emitter won't drop
        // the strap. Karoo recreates the emitter across lifecycle changes (e.g. at
        // ride start) — tearing the link down on each cancel dropped it mid-ride.
        val settings = RiderSettings(applicationContext)
        if (uid == VIRTUAL_HR_UID) {
            // Virtual entry: reconnect the remembered strap, else connect the first
            // one found and remember it (so future connects reuse the same strap).
            bleManager.connectStrap(settings.pairedStrapMac) { mac -> settings.pairedStrapMac = mac }
        } else {
            // A specific strap the rider picked from the scanned list.
            settings.pairedStrapMac = uid
            bleManager.ensureConnected(uid)
        }
        val job: Job = CoroutineScope(Dispatchers.IO).launch {
            // connectedFlow is a StateFlow, so it always replays the current link
            // state to a freshly-recreated emitter (the event stream below carries
            // only future transitions).
            launch {
                bleManager.connectedFlow.collect { connected ->
                    emitter.onNext(
                        OnConnectionStatus(
                            if (connected) ConnectionStatus.CONNECTED
                            else ConnectionStatus.SEARCHING,
                        )
                    )
                }
            }
            bleManager.events().collect { event ->
                if (event is PolarBleManager.BleEvent.Heartrate) {
                    emitter.onNext(
                        OnDataPoint(
                            DataPoint(
                                dataTypeId = DataType.Type.HEART_RATE,
                                // The HR data type reads its value from
                                // Field.HEART_RATE; under Field.SINGLE it shows but
                                // is never recorded to the FIT. Tag the sourceId
                                // with the device uid so Karoo logs it as this sensor.
                                values = mapOf(DataType.Field.HEART_RATE to event.bpm.toDouble()),
                                sourceId = uid,
                            )
                        )
                    )
                }
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun startFit(emitter: Emitter<FitEffect>) {
        // A new recording session = a new ride: reset the per-ride power metrics
        // so best-power, TSS, kJ etc. count this ride, not the previous one. Same
        // for DFA α1 — its long window otherwise shows a stale pre-ride value the
        // instant recording starts; reset it so it warms up fresh for this ride.
        ridePowerEngine.resetRide()
        bleManager.resetDfaAlpha1()
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
        // Mirror the live AeT estimate into the FIT record while recording; the
        // engine owns the calculator so it accumulates for the whole ride.
        val aetJob: Job = scope.launch {
            ridePowerEngine.aet.filterNotNull().collect { est ->
                emitter.onNext(WriteToRecordMesg(FieldValue(AET_FIELD, est.toDouble())))
            }
        }
        // Single periodic writer for the after-ride session summaries. The engine
        // owns the calculators (they accumulate for the whole ride regardless of
        // page); we snapshot them here. WriteToSessionMesg keeps the latest value,
        // so the final tick before the ride ends carries the summary numbers.
        val summaryJob: Job = scope.launch {
            while (isActive) {
                delay(SESSION_SUMMARY_INTERVAL_MS)
                val snapshot = RideSummarySnapshot(
                    aet = ridePowerEngine.aetCurrentEstimate(),
                    vt2 = ridePowerEngine.vt2CurrentEstimate(),
                    optimalCadence = ridePowerEngine.optimalCadenceCurrent(),
                    wPrimeMinJ = ridePowerEngine.wPrimeMinJ(),
                    matchesBurned = ridePowerEngine.matchesBurnedCount,
                    dfaAerobicS = ridePowerEngine.dfaAerobicSeconds(),
                    dfaThresholdS = ridePowerEngine.dfaThresholdSeconds(),
                    dfaHardS = ridePowerEngine.dfaHardSeconds(),
                    cardiacPopMin = ridePowerEngine.cardiacPop.value,
                    quadrantPct = ridePowerEngine.quadrantDistribution(),
                )
                for (field in sessionSummaryFields(snapshot)) {
                    emitter.onNext(WriteToSessionMesg(field))
                }
            }
        }
        emitter.setCancellable {
            rmssdJob.cancel()
            stressJob.cancel()
            dfaJob.cancel()
            respJob.cancel()
            sdnnJob.cancel()
            aetJob.cancel()
            summaryJob.cancel()
            val final = ridePowerEngine.aetCurrentEstimate()
            val samples = ridePowerEngine.aetSampleCount
            if (shouldPersistRollingFinal(final, samples, MIN_AET_SAMPLES_TO_PERSIST)) {
                AerobicThresholdStore(applicationContext)
                    .record(System.currentTimeMillis(), final!!, samples)
            }
            val cadenceFinal = ridePowerEngine.optimalCadenceCurrent()
            val cadenceSamples = ridePowerEngine.optimalCadenceSamples
            if (shouldPersistRollingFinal(cadenceFinal, cadenceSamples, MIN_CADENCE_SAMPLES_TO_PERSIST)) {
                OptimalCadenceStore(applicationContext)
                    .record(System.currentTimeMillis(), cadenceFinal!!, cadenceSamples)
            }
        }
    }

    override fun onDestroy() {
        // Deliberately do NOT disconnect the strap or release BT here: Karoo
        // recreates the service at ride start, and dropping the link (status=22
        // local teardown) then auto-reconnecting was the ~4 s "Searching" gap.
        // The strap link is process-scoped (PolarBleManager singleton) and the
        // SDK keeps it alive across the recreate.
        serviceScope.cancel()
        karooSystem.disconnect()
        super.onDestroy()
    }
}
