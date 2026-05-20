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
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
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
 * The public surface (BleEvent, connect(), startDeviceScan(), the HRV
 * StateFlows) is unchanged so the rest of the extension is unaffected.
 */
class PolarBleManager(private val context: Context) {

    sealed class BleEvent {
        object Connected : BleEvent()
        object Disconnected : BleEvent()
        data class Heartrate(val bpm: Int) : BleEvent()
    }

    /** A strap found during [startDeviceScan]. [id] is the BT MAC address. */
    data class DiscoveredDevice(val id: String, val name: String)

    private val api: PolarBleApi by lazy { sharedApi(context) }

    companion object {
        // After a gap longer than this, the retained HRV windows are stale and
        // get cleared on reconnect; shorter gaps keep them so values resume at
        // once instead of refilling from scratch.
        private const val STALE_GAP_MS = 15_000L

        // A single Polar BLE stack for the whole process. The extension service
        // and the Readiness screen each hold a PolarBleManager; without this they
        // would spin up two BLE stacks that fight over the radio and cause drops.
        @Volatile
        private var sharedApiInstance: PolarBleApi? = null

        private fun sharedApi(context: Context): PolarBleApi =
            sharedApiInstance ?: synchronized(this) {
                sharedApiInstance ?: PolarBleApiDefaultImpl.defaultImplementation(
                    context.applicationContext,
                    setOf(
                        PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                        PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                        PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
                    ),
                ).apply { setAutomaticReconnection(true) }.also { sharedApiInstance = it }
            }
    }

    // Tracks the active scan so we can stop it before connecting: the Polar SDK
    // becomes unstable (connects then immediately drops) if a scan is still
    // running on the same API instance during connectToDevice().
    @Volatile
    private var scanDisposable: Disposable? = null

    private val _rmssdFlow = MutableStateFlow(0f)
    val rmssdFlow: StateFlow<Float> = _rmssdFlow.asStateFlow()
    private val calculator = HRVCalculator(windowSize = 30)

    private val _stressFlow = MutableStateFlow<Float?>(null)
    val stressFlow: StateFlow<Float?> = _stressFlow.asStateFlow()
    private val stressCalculator = HRVStressCalculator()

    private val _dfaAlpha1Flow = MutableStateFlow<Float?>(null)
    val dfaAlpha1Flow: StateFlow<Float?> = _dfaAlpha1Flow.asStateFlow()
    private val dfaCalculator = DfaAlpha1Calculator()
    // DFA α1 is very artifact-sensitive, so its window is fed only the RR
    // intervals that survive artifact rejection (the other metrics are far more
    // tolerant and keep using the raw stream).
    private val dfaArtifactCorrector = RrArtifactCorrector()

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
        dfaArtifactCorrector.reset()
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

    fun connect(macAddress: String): Flow<BleEvent> = callbackFlow {
        val scope = this
        var hrDisposable: Disposable? = null

        // Never scan and connect at the same time (Polar SDK pitfall).
        scanDisposable?.dispose()
        scanDisposable = null

        // The SDK identifies the device in callbacks by its Polar device id
        // (e.g. "B36B5B2C"), NOT the BT MAC address we connect with, so we
        // must not match against `address` here. This manager only ever
        // connects to one device per connect(), so no disambiguation is needed.
        var disconnectedAt = 0L

        val callback = object : PolarBleApiCallback() {
            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
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
                scope.trySend(BleEvent.Connected)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                disconnectedAt = System.currentTimeMillis()
                _connectedFlow.value = false
                hrDisposable?.dispose()
                hrDisposable = null
                // Blank the fields but keep the windows; a fast reconnect resumes
                // at once. resetHrvCalculators() runs on a long gap / teardown.
                clearHrvOutputs()
                scope.trySend(BleEvent.Disconnected)
                // setAutomaticReconnection(true) makes the SDK keep retrying;
                // we just clear local state and wait for the next deviceConnected.
            }

            override fun disInformationReceived(identifier: String, disInfo: DisInfo) {
                // Device information messages — no-op; the SDK requires this overload
                // (it's abstract on the callback provider interface).
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
                        { hrData -> consumeHrData(hrData, scope) },
                        { /* stream error — SDK will fire deviceDisconnected if relevant */ },
                    )
            }
        }

        api.setApiCallback(callback)
        runCatching { api.connectToDevice(macAddress) }

        awaitClose {
            hrDisposable?.dispose()
            runCatching { api.disconnectFromDevice(macAddress) }
            _connectedFlow.value = false
            resetHrvCalculators()
        }
    }

    private fun consumeHrData(
        hrData: PolarHrData,
        scope: kotlinx.coroutines.channels.SendChannel<BleEvent>,
    ) {
        for (sample in hrData.samples) {
            if (sample.hr > 0) scope.trySend(BleEvent.Heartrate(sample.hr))
            if (!sample.rrAvailable) continue
            var anyRr = false
            for (rr in sample.rrsMs) {
                if (rr <= 0) continue
                anyRr = true
                calculator.addInterval(rr)
                dfaArtifactCorrector.accept(rr)?.let { dfaCalculator.addInterval(it) }
                sdnnCalculator.addInterval(rr)
                pnn50Calculator.addInterval(rr)
                poincareCalculator.addInterval(rr)
                respiratoryRateCalculator.addInterval(rr)
                ectopicDetector.addInterval(rr)
            }
            if (!anyRr) continue
            if (calculator.hasData) {
                val rmssd = calculator.getRmssd()
                _rmssdFlow.value = rmssd
                stressCalculator.addRmssd(rmssd)
                _stressFlow.value = stressCalculator.getStressPct()
            }
            _dfaAlpha1Flow.value = dfaCalculator.getAlpha1()
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
