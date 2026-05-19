package com.inqulab.heartkaroo.hrv

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import java.util.UUID

class PolarBleManager(private val context: Context) {

    companion object {
        val HR_SERVICE_UUID: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val HR_MEASUREMENT_UUID: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    sealed class BleEvent {
        object Connected : BleEvent()
        object Disconnected : BleEvent()
        data class Heartrate(val bpm: Int) : BleEvent()
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter

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

    fun startDeviceScan(onDevice: (BluetoothDevice) -> Unit): () -> Unit {
        val leScanner = bluetoothAdapter?.bluetoothLeScanner ?: return {}
        val seenAddresses = mutableSetOf<String>()
        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (seenAddresses.add(result.device.address)) onDevice(result.device)
            }
        }
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE_UUID)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        leScanner.startScan(listOf(filter), settings, scanCallback)
        return { leScanner.stopScan(scanCallback) }
    }

    fun connect(address: String): Flow<BleEvent> = callbackFlow @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT) {
        val device = bluetoothAdapter.getRemoteDevice(address)
        val scope = this
        var currentGatt: BluetoothGatt? = null

        val gattCallback = object : BluetoothGattCallback() {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        currentGatt = gatt
                        scope.trySend(BleEvent.Connected)
                        gatt.discoverServices()
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        currentGatt = null
                        scope.trySend(BleEvent.Disconnected)
                        gatt.close()
                        if (scope.isActive) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                if (scope.isActive) {
                                    device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE)
                                }
                            }, 5_000)
                        }
                    }
                }
            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status != BluetoothGatt.GATT_SUCCESS) return
                val hrChar = gatt.getService(HR_SERVICE_UUID)
                    ?.getCharacteristic(HR_MEASUREMENT_UUID) ?: return
                gatt.setCharacteristicNotification(hrChar, true)
                val cccd = hrChar.getDescriptor(CCCD_UUID) ?: return
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(cccd)
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) = handlePayload(value)

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
            ) {
                @Suppress("DEPRECATION")
                handlePayload(characteristic.value)
            }

            private fun handlePayload(data: ByteArray) {
                val parsed = parseHeartRateMeasurement(data) ?: return
                scope.trySend(BleEvent.Heartrate(parsed.bpm))
                for (rr in parsed.rrIntervalsMs) {
                    calculator.addInterval(rr)
                    dfaCalculator.addInterval(rr)
                    sdnnCalculator.addInterval(rr)
                    pnn50Calculator.addInterval(rr)
                    poincareCalculator.addInterval(rr)
                    respiratoryRateCalculator.addInterval(rr)
                    ectopicDetector.addInterval(rr)
                }
                if (parsed.rrIntervalsMs.isNotEmpty() && calculator.hasData) {
                    val rmssd = calculator.getRmssd()
                    _rmssdFlow.value = rmssd
                    stressCalculator.addRmssd(rmssd)
                    _stressFlow.value = stressCalculator.getStressPct()
                }
                if (parsed.rrIntervalsMs.isNotEmpty()) {
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

        currentGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)

        awaitClose {
            currentGatt?.disconnect()
            currentGatt?.close()
            currentGatt = null
        }
    }
}
