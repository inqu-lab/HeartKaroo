package com.inqulab.heartkaroo

import com.inqulab.heartkaroo.decoupling.DecouplingDataType
import com.inqulab.heartkaroo.hrv.DfaAlpha1DataType
import com.inqulab.heartkaroo.hrv.HRVDataType
import com.inqulab.heartkaroo.hrv.HRVStressDataType
import com.inqulab.heartkaroo.hrv.HrvFlowDataType
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
            fieldName = "dfa_alpha1",
            units = "",
        )

        val RESPIRATORY_RATE_FIELD = DeveloperField(
            fieldDefinitionNumber = 3,
            fitBaseTypeId = 136,
            fieldName = "respiratory_rate",
            units = "brpm",
        )

        val SDNN_FIELD = DeveloperField(
            fieldDefinitionNumber = 4,
            fitBaseTypeId = 136,
            fieldName = "sdnn",
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
            HRVStressDataType(bleManager, EXTENSION_ID),
            DfaAlpha1DataType(bleManager, EXTENSION_ID),
            HrvFlowDataType(EXTENSION_ID, "hrv_sdnn", bleManager.sdnnFlow),
            HrvFlowDataType(EXTENSION_ID, "hrv_pnn50", bleManager.pnn50Flow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd1", bleManager.sd1Flow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd2", bleManager.sd2Flow),
            HrvFlowDataType(EXTENSION_ID, "hrv_sd1_sd2_ratio", bleManager.sd1Sd2RatioFlow),
            HrvFlowDataType(EXTENSION_ID, "respiratory_rate", bleManager.respiratoryRateFlow),
            HrvFlowDataType(EXTENSION_ID, "ectopic_rate", bleManager.ectopicRateFlow),
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
        emitter.setCancellable {
            rmssdJob.cancel()
            stressJob.cancel()
            dfaJob.cancel()
            respJob.cancel()
            sdnnJob.cancel()
        }
    }

    override fun onDestroy() {
        karooSystem.disconnect()
        super.onDestroy()
    }
}
