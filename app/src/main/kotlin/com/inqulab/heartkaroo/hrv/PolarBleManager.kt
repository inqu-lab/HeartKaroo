package com.inqulab.heartkaroo.hrv

import android.content.Context
import com.polar.androidcommunications.api.ble.model.DisInfo
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarHrData
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Collections

/**
 * Wraps Polar's official BLE SDK so the Karoo extension talks to the H10
 * (or any modern Polar HR strap) through the same channel Polar's own app
 * uses. The SDK gives us:
 *
 *  - automatic reconnection when the strap briefly leaves range
 *  - per-beat RR intervals at the H10's native precision (rather than the
 *    standard 2A37 characteristic, which truncates to 1/1024 s and drops
 *    intermediate beats between notifications)
 *
 * The BLE link is owned by the manager and lives independently of any flow
 * collector: it is opened with [ensureConnected]/[connect] and stays up for the
 * life of the process, so a strap stays connected even as Karoo tears down and
 * recreates the service (e.g. when a ride starts). The manager is a process-wide
 * singleton ([getInstance]) shared by the extension service and the Readiness
 * screen, so there is exactly one strap link — switched via [ensureConnected],
 * never dropped on a service/Activity teardown.
 *
 * The SDK refreshes the link itself on occasion (a brief local disconnect +
 * reconnect, e.g. an idle→active interval change at ride start). That isn't ours
 * to prevent, so [deviceDisconnected] holds the reported connection state for a
 * short grace window: a quick reconnect never surfaces as "Searching".
 */
class PolarBleManager private constructor(private val context: Context) {

    sealed class BleEvent {
        object Connected : BleEvent()
        object Disconnected : BleEvent()
        data class Heartrate(val bpm: Int) : BleEvent()
    }

    /** A strap found during [startDeviceScan]. [id] is the BT MAC address. */
    data class DiscoveredDevice(val id: String, val name: String)

    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context.applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            ),
        ).apply { setAutomaticReconnection(true) }
    }

    companion object {
        // After a gap longer than this, the retained HRV windows are stale and
        // get cleared on reconnect; shorter gaps keep them so values resume at
        // once instead of refilling from scratch.
        private const val STALE_GAP_MS = 15_000L

        // DFA α1 is withheld when more than this fraction of recent beats are
        // artifacts — past a few percent the exponent is no longer trustworthy.
        private const val MAX_ALPHA1_ARTIFACT_RATE = 0.05

        // How long to keep reporting "connected" after a disconnect, to ride out
        // the SDK's own quick reconnect without flashing Searching.
        private const val RECONNECT_GRACE_MS = 8_000L

        // One manager — and therefore one Polar BLE stack and one strap link —
        // for the whole process. The extension service and the Readiness screen
        // share it so the strap stays connected as the service is torn down and
        // recreated (notably at ride start) instead of being dropped and
        // reconnected. Two separate managers used to fight over the radio and the
        // shared SDK callback, and a service teardown would disconnect the strap.
        @Volatile
        private var instance: PolarBleManager? = null

        fun getInstance(context: Context): PolarBleManager =
            instance ?: synchronized(this) {
                instance ?: PolarBleManager(context.applicationContext).also { instance = it }
            }
    }

    // Tracks the active scan so we can stop it before connecting: the Polar SDK
    // becomes unstable (connects then immediately drops) if a scan is still
    // running on the same API instance during connectToDevice().
    @Volatile
    private var scanDisposable: Disposable? = null

    // The live BLE link is owned by this manager, not by any single flow
    // collector. These track that link so it survives Karoo tearing down and
    // recreating the connectDevice emitter (which happens at ride start).
    @Volatile
    private var hrDisposable: Disposable? = null
    @Volatile
    private var connectingMac: String? = null
    private var disconnectedAt = 0L

    // Process-scoped (the manager is a singleton): defers the "really
    // disconnected" flip so a quick auto-reconnect doesn't flash Searching.
    private val managerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var reconnectGraceJob: Job? = null

    // Hot, shared event stream. Collectors come and go (each connectDevice or
    // Readiness subscription); the connection underneath them does not.
    private val events = MutableSharedFlow<BleEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _rmssdFlow = MutableStateFlow(0f)
    val rmssdFlow: StateFlow<Float> = _rmssdFlow.asStateFlow()
    private val calculator = HRVCalculator(windowSize = 30)

    private val _stressFlow = MutableStateFlow<Float?>(null)
    val stressFlow: StateFlow<Float?> = _stressFlow.asStateFlow()
    private val stressCalculator = HRVStressCalculator()

    private val _dfaAlpha1Flow = MutableStateFlow<Float?>(null)
    val dfaAlpha1Flow: StateFlow<Float?> = _dfaAlpha1Flow.asStateFlow()
    private val dfaCalculator = DfaAlpha1Calculator()
    // The variability metrics (RMSSD/stress, SDNN, pNN50, Poincaré, DFA α1) are
    // all artifact-sensitive, so they are fed only the RR intervals that survive
    // artifact rejection. The ectopic detector and respiratory rate keep using
    // the raw stream (the former literally counts artifacts; the latter is
    // timing-sensitive and shouldn't have beats removed).
    private val artifactCorrector = RrArtifactCorrector()

    private val _sdnnFlow = MutableStateFlow<Float?>(null)
    val sdnnFlow: StateFlow<Float?> = _sdnnFlow.asStateFlow()
    private val sdnnCalculator = SdnnCalculator()

    private val _pnn50Flow = MutableStateFlow<Float?>(null)
    val pnn50Flow: StateFlow<Float?> = _pnn50Flow.asStateFlow()
    private val pnn50Calculator = Pnn50Calculator()

    private val _sd1Flow = MutableStateFlow<Float?>(null)
    val sd1Flow: StateFlow<Float?> = _sd1Flow.asStateFlow()
    private val _sd2Flow = MutableStateFlow<Float?>(null)
    val sd2Flow: StateFlow<Float?> = _sd2Flow.asStateFlow()
    private val _sd1Sd2RatioFlow = MutableStateFlow<Float?>(null)
    val sd1Sd2RatioFlow: StateFlow<Float?> = _sd1Sd2RatioFlow.asStateFlow()
    private val poincareCalculator = PoincareCalculator()

    private val _respiratoryRateFlow = MutableStateFlow<Float?>(null)
    val respiratoryRateFlow: StateFlow<Float?> = _respiratoryRateFlow.asStateFlow()
    private val respiratoryRateCalculator = RespiratoryRateCalculator()

    private val _ectopicRateFlow = MutableStateFlow<Float?>(null)
    val ectopicRateFlow: StateFlow<Float?> = _ectopicRateFlow.asStateFlow()
    private val ectopicDetector = EctopicDetector()

    private val _connectedFlow = MutableStateFlow(false)
    val connectedFlow: StateFlow<Boolean> = _connectedFlow.asStateFlow()

    /** Strap battery level in percent, or null until reported. */
    private val _batteryFlow = MutableStateFlow<Int?>(null)
    val batteryFlow: StateFlow<Int?> = _batteryFlow.asStateFlow()

    /** Whether the strap currently has good skin contact (true when the strap
     *  doesn't report contact at all). HRV is not computed while contact is lost. */
    private val _contactOkFlow = MutableStateFlow(true)
    val contactOkFlow: StateFlow<Boolean> = _contactOkFlow.asStateFlow()

    /** Blanks the published values (fields show "Searching") without dropping
     *  the accumulated RR windows, so a quick reconnect resumes immediately. */
    private fun clearHrvOutputs() {
        _rmssdFlow.value = 0f
        _stressFlow.value = null
        _dfaAlpha1Flow.value = null
        _sdnnFlow.value = null
        _pnn50Flow.value = null
        _sd1Flow.value = null
        _sd2Flow.value = null
        _sd1Sd2RatioFlow.value = null
        _respiratoryRateFlow.value = null
        _ectopicRateFlow.value = null
    }

    private fun resetHrvCalculators() {
        calculator.reset()
        stressCalculator.reset()
        dfaCalculator.reset()
        artifactCorrector.reset()
        sdnnCalculator.reset()
        pnn50Calculator.reset()
        poincareCalculator.reset()
        respiratoryRateCalculator.reset()
        ectopicDetector.reset()
        clearHrvOutputs()
    }

    fun startDeviceScan(onDevice: (DiscoveredDevice) -> Unit): () -> Unit {
        // Identify the strap by its BT MAC address, not the Polar device id:
        // the MAC lets connectToDevice() open a DIRECT GATT connection, whereas
        // connecting by device id makes the SDK re-scan to resolve it first —
        // which is slow and was dropping the link immediately after pairing.
        // searchForDevice() re-emits the same strap repeatedly, so dedupe.
        val seen = Collections.synchronizedSet(mutableSetOf<String>())
        scanDisposable?.dispose()
        val disposable = api.searchForDevice()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { info ->
                    val id = info.address
                    if (id.isNotBlank() && info.isConnectable && seen.add(id)) {
                        onDevice(DiscoveredDevice(id, info.name.ifBlank { "Polar HRM" }))
                    }
                },
                { /* ignore scan errors */ },
            )
        scanDisposable = disposable
        return {
            disposable.dispose()
            if (scanDisposable === disposable) scanDisposable = null
        }
    }

    /**
     * Ensures a BLE link to [macAddress] and returns the shared [BleEvent]
     * stream. The link is owned by the manager, NOT by the returned flow:
     * cancelling a collector stops delivery to that collector but leaves the
     * strap connected. Karoo tears down and recreates the connectDevice emitter
     * across lifecycle changes (notably when a ride starts); disconnecting on
     * every teardown was dropping the strap mid-ride, and because
     * disconnectFromDevice is an explicit disconnect the SDK would not
     * auto-reconnect afterwards. Call [disconnect] to actually drop the link.
     */
    fun connect(macAddress: String): Flow<BleEvent> {
        ensureConnected(macAddress)
        return events.asSharedFlow()
    }

    /** The shared connection/HR event stream, decoupled from the link lifecycle. */
    fun events(): Flow<BleEvent> = events.asSharedFlow()

    /** Connect the rider's strap when only a virtual sensor entry was paired (no
     *  MAC chosen from a scan): reconnect [preferredMac] if known, otherwise scan
     *  and connect the first Polar found, reporting its MAC via [onResolvedMac] so
     *  the caller can remember it and reuse the same strap next time. */
    fun connectStrap(preferredMac: String?, onResolvedMac: (String) -> Unit) {
        if (preferredMac != null) {
            ensureConnected(preferredMac)
            return
        }
        var stop: (() -> Unit)? = null
        stop = startDeviceScan { device ->
            stop?.invoke()
            stop = null
            onResolvedMac(device.id)
            ensureConnected(device.id)
        }
    }

    /** Idempotently open the link to [macAddress]. (Re)claims the shared SDK
     *  callback for this manager and only issues a connect when the strap
     *  changes, so a repeat call for the same strap is a no-op that won't bounce
     *  the link. */
    @Synchronized
    fun ensureConnected(macAddress: String) {
        // Never scan and connect at the same time (Polar SDK pitfall).
        scanDisposable?.dispose()
        scanDisposable = null
        // The Polar SDK keeps a single callback per API instance, and this
        // process shares one API across the service and the Readiness screen, so
        // (re)claim it for whichever manager is currently driving the link.
        api.setApiCallback(apiCallback)
        if (connectingMac == macAddress) return
        connectingMac?.let { old -> runCatching { api.disconnectFromDevice(old) } }
        connectingMac = macAddress
        runCatching { api.connectToDevice(macAddress) }
    }

    /** Drop the BLE link and reset HRV state. Intentionally NOT called on a
     *  service or Activity teardown — the link is process-scoped and must outlive
     *  them (a ride start recreates the service). Reserved for a deliberate full
     *  teardown; routine strap switching goes through [ensureConnected]. */
    @Synchronized
    fun disconnect() {
        reconnectGraceJob?.cancel()
        reconnectGraceJob = null
        hrDisposable?.dispose()
        hrDisposable = null
        connectingMac?.let { mac -> runCatching { api.disconnectFromDevice(mac) } }
        connectingMac = null
        _connectedFlow.value = false
        resetHrvCalculators()
    }

    /** Reset the DFA α1 window so it warms up fresh for a new ride. The manager
     *  is process-scoped and the strap stays connected between rides, so α1's long
     *  (~2 min / up to 480-beat) window would otherwise carry pre-ride data and
     *  show a stale value the instant recording starts. Call at ride start. The
     *  short-window metrics (RMSSD, SDNN, …) self-refresh in seconds and are left
     *  live. */
    @Synchronized
    fun resetDfaAlpha1() {
        dfaCalculator.reset()
        _dfaAlpha1Flow.value = null
    }

    // The SDK identifies the device in callbacks by its Polar device id
    // (e.g. "B36B5B2C"), NOT the BT MAC we connect with, so we don't match on the
    // MAC here. Only one strap is connected at a time.
    private val apiCallback: PolarBleApiCallback by lazy {
        object : PolarBleApiCallback() {
            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                // A reconnect landed within the grace window — cancel the pending
                // "really disconnected" flip so fields never flickered.
                reconnectGraceJob?.cancel()
                reconnectGraceJob = null
                // Drop the retained HRV windows only if the gap was long enough
                // that they'd be stale; a brief auto-reconnect keeps them so the
                // fields resume immediately instead of refilling for ~30 beats.
                if (disconnectedAt != 0L &&
                    System.currentTimeMillis() - disconnectedAt > STALE_GAP_MS
                ) {
                    resetHrvCalculators()
                }
                disconnectedAt = 0L
                _connectedFlow.value = true
                events.tryEmit(BleEvent.Connected)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                disconnectedAt = System.currentTimeMillis()
                hrDisposable?.dispose()
                hrDisposable = null
                // The SDK auto-reconnects, and its own connect/reconnect cycle
                // (seen at ride start) drops then restores the link within a few
                // seconds. Don't surface that as a loss immediately: keep
                // "connected" and the last values through a short grace window so
                // fields don't flash Searching. Only if the strap is genuinely
                // gone does the window elapse and we report the disconnect.
                reconnectGraceJob?.cancel()
                reconnectGraceJob = managerScope.launch {
                    delay(RECONNECT_GRACE_MS)
                    _connectedFlow.value = false
                    clearHrvOutputs()
                    events.tryEmit(BleEvent.Disconnected)
                    reconnectGraceJob = null
                }
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                // Device information messages — no-op; the SDK requires this overload
                // (it's abstract on the callback provider interface).
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                _batteryFlow.value = level
            }

            override fun bleSdkFeatureReady(
                identifier: String,
                feature: PolarBleApi.PolarBleSdkFeature,
            ) {
                if (feature != PolarBleApi.PolarBleSdkFeature.FEATURE_HR) return
                hrDisposable?.dispose()
                hrDisposable = api.startHrStreaming(identifier)
                    .observeOn(Schedulers.computation())
                    .subscribe(
                        { hrData -> consumeHrData(hrData) },
                        { /* stream error — SDK will fire deviceDisconnected if relevant */ },
                    )
            }
        }
    }

    private fun consumeHrData(hrData: PolarHrData) {
        for (sample in hrData.samples) {
            if (sample.hr > 0) events.tryEmit(BleEvent.Heartrate(sample.hr))

            val contactOk = !sample.contactStatusSupported || sample.contactStatus
            _contactOkFlow.value = contactOk

            if (!sample.rrAvailable) continue
            // Poor skin contact produces noise, not heartbeats — don't let it
            // pollute the HRV windows.
            if (!contactOk) continue

            var anyRr = false
            for (rr in sample.rrsMs) {
                if (rr <= 0) continue
                anyRr = true
                // Raw stream: ectopic count and respiratory rate.
                ectopicDetector.addInterval(rr)
                respiratoryRateCalculator.addInterval(rr)
                // Artifact-corrected stream: the variability metrics.
                artifactCorrector.accept(rr)?.let { clean ->
                    calculator.addInterval(clean)
                    dfaCalculator.addInterval(clean)
                    sdnnCalculator.addInterval(clean)
                    pnn50Calculator.addInterval(clean)
                    poincareCalculator.addInterval(clean)
                }
            }
            if (!anyRr) continue
            if (calculator.hasData) {
                val rmssd = calculator.getRmssd()
                _rmssdFlow.value = rmssd
                stressCalculator.addRmssd(rmssd)
                _stressFlow.value = stressCalculator.getStressPct()
            }
            // Withhold α1 when the recent artifact rate is too high to trust it.
            _dfaAlpha1Flow.value =
                if (artifactCorrector.recentArtifactRate() > MAX_ALPHA1_ARTIFACT_RATE) null
                else dfaCalculator.getAlpha1()
            _sdnnFlow.value = sdnnCalculator.getSdnn()
            _pnn50Flow.value = pnn50Calculator.getPnn50()
            val pc = poincareCalculator.getResult()
            _sd1Flow.value = pc?.sd1
            _sd2Flow.value = pc?.sd2
            _sd1Sd2RatioFlow.value = pc?.ratio
            _respiratoryRateFlow.value = respiratoryRateCalculator.getBreathsPerMin()
            _ectopicRateFlow.value = ectopicDetector.getEventsPerMin()
        }
    }
}
