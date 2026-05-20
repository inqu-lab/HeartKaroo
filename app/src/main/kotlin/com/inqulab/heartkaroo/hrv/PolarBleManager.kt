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

    /** A strap found during [startDeviceScan]. */
    data class DiscoveredDevice(val address: String, val name: String)

    private val api: PolarBleApi by lazy {
        PolarBleApiDefaultImpl.defaultImplementation(
            context.applicationContext,
            setOf(
                PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
                PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
                PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO,
            ),
        ).apply {
            setAutomaticReconnection(true)
        }
    }

    private val _rmssdFlow = MutableStateFlow(0f)
    val rmssdFlow: StateFlow<Float> = _rmssdFlow.asStateFlow()
    private val calculator = HRVCalculator(windowSize = 30)

    private val _stressFlow = MutableStateFlow<Float?>(null)
    val stressFlow: StateFlow<Float?> = _stressFlow.asStateFlow()
    private val stressCalculator = HRVStressCalculator()

    private val _dfaAlpha1Flow = MutableStateFlow<Float?>(null)
    val dfaAlpha1Flow: StateFlow<Float?> = _dfaAlpha1Flow.asStateFlow()
    private val dfaCalculator = DfaAlpha1Calculator()

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

    private fun resetHrvCalculators() {
        calculator.reset()
        stressCalculator.reset()
        dfaCalculator.reset()
        sdnnCalculator.reset()
        pnn50Calculator.reset()
        poincareCalculator.reset()
        respiratoryRateCalculator.reset()
        ectopicDetector.reset()
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

    fun startDeviceScan(onDevice: (DiscoveredDevice) -> Unit): () -> Unit {
        // searchForDevice() can re-emit the same strap as it keeps scanning;
        // dedupe by address so the strap is offered to Karoo only once.
        val seen = Collections.synchronizedSet(mutableSetOf<String>())
        val disposable = api.searchForDevice()
            .subscribeOn(Schedulers.io())
            .subscribe(
                { info ->
                    if (seen.add(info.address)) {
                        val name = info.name.ifBlank { "Polar HRM" }
                        onDevice(DiscoveredDevice(info.address, name))
                    }
                },
                { /* ignore scan errors */ },
            )
        return { disposable.dispose() }
    }

    fun connect(address: String): Flow<BleEvent> = callbackFlow {
        val scope = this
        var hrDisposable: Disposable? = null

        val callback = object : PolarBleApiCallback() {
            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                if (!polarDeviceInfo.address.equals(address, ignoreCase = true)) return
                _connectedFlow.value = true
                scope.trySend(BleEvent.Connected)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                if (!polarDeviceInfo.address.equals(address, ignoreCase = true)) return
                _connectedFlow.value = false
                hrDisposable?.dispose()
                hrDisposable = null
                resetHrvCalculators()
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
                if (!identifier.equals(address, ignoreCase = true)) return
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
        runCatching { api.connectToDevice(address) }

        awaitClose {
            hrDisposable?.dispose()
            runCatching { api.disconnectFromDevice(address) }
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
                dfaCalculator.addInterval(rr)
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
