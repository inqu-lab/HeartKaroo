package com.inqulab.heartkaroo.power

import com.inqulab.heartkaroo.HeartKarooExtension
import com.inqulab.heartkaroo.karoo.streamDataFlow
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

class VariabilityIndexDataType(
    private val parent: HeartKarooExtension,
) : DataTypeImpl(parent.extension, TYPE_ID) {

    companion object {
        const val TYPE_ID = "variability_index"
        const val FIELD = "variability_index"
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val np = NormalizedPowerCalculator()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val job: Job = scope.launch {
            parent.karooSystem.streamDataFlow(DataType.Type.POWER).collect { ps ->
                val p = (ps as? StreamState.Streaming)?.dataPoint?.values?.values?.firstOrNull()
                    ?: return@collect
                np.add(System.currentTimeMillis(), p)
                val n = np.normalizedPower()
                val a = np.averagePower()
                val vi = if (n != null && a != null && a > 0f) n / a else null
                emitter.onNext(
                    if (vi == null) StreamState.Searching
                    else StreamState.Streaming(DataPoint(dataTypeId, mapOf(FIELD to vi.toDouble()))),
                )
            }
        }
        emitter.setCancellable { job.cancel(); scope.cancel() }
    }

    // VI is a ~1.0–1.3 ratio; without a format hint Karoo renders it as a whole
    // number. Use the native Variability Index formatting (two decimals).
    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        emitter.onNext(UpdateNumericConfig(formatDataTypeId = DataType.Type.VARIABILITY_INDEX))
        emitter.setCancellable {}
    }
}
