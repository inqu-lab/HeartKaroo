package com.inqulab.heartkaroo

import com.inqulab.heartkaroo.decoupling.DecouplingDataType
import com.inqulab.heartkaroo.hrv.HRVDataType
import com.inqulab.heartkaroo.hrv.PolarBleManager
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
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import io.hammerhead.karooext.models.WriteToRecordMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filter
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
    }

    lateinit var karooSystem: KarooSystemService
        private set

    lateinit var bleManager: PolarBleManager
        private set

    override val types by lazy {
        listOf(
            DecouplingDataType(this),
            HRVDataType(bleManager, EXTENSION_ID),
        )
    }

    override fun onCreate() {
        super.onCreate()
        karooSystem = KarooSystemService(applicationContext)
        bleManager = PolarBleManager(applicationContext)
        karooSystem.connect {}
    }

    override fun startScan(emitter: Emitter<Device>) {
        val stop = bleManager.startDeviceScan @androidx.annotation.RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT) { btDevice ->
            emitter.onNext(
                Device(
                    extension = EXTENSION_ID,
                    uid = btDevice.address,
                    dataTypes = listOf(DataType.Type.HEART_RATE),
                    displayName = btDevice.name ?: "Polar H10",
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
        val job: Job = CoroutineScope(Dispatchers.IO).launch {
            bleManager.rmssdFlow
                .filter { it > 0f }
                .collect { rmssd ->
                    emitter.onNext(WriteToRecordMesg(FieldValue(RMSSD_FIELD, rmssd.toDouble())))
                }
        }
        emitter.setCancellable { job.cancel() }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}
