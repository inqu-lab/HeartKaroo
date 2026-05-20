package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
import com.inqulab.heartkaroo.settings.RiderSettings
import android.content.Context
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UpdateNumericConfig
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class IntensityFactorDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "intensity_factor"
        const val FIELD = "intensity_factor"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val np = NormalizedPowerCalculator()
        val settings = RiderSettings(parent.applicationContext)
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            parent.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                np.add(System.currentTimeMillis(), p)
                val ftp = settings.ftpW.coerceAtLeast(1)
                val ifVal = np.normalizedPower()?.let { it / ftp }
                emitter.onNext(
                    if (ifVal == null) StreamState.Searching
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to ifVal.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }

    // IF is a ~0.7–1.1 ratio; without a format hint Karoo renders it as a whole
    // number. Use the native Intensity Factor formatting (two decimals).
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.INTENSITY_FACTOR))
        emitter.setCancellable {}
    }
}
