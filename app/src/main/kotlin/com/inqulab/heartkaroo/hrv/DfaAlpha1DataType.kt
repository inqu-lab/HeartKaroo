package com.inqulab.heartkaroo.hrv

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import com.inqulab.heartkaroo.R
import io.hammerhead.karooext.extension.DataTypeImpl
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.internal.ViewEmitter
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.ViewConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Custom data field exposed to Karoo as "DFA α1".
 *
 * Reads the α1 flow maintained by PolarBleManager, recomputed each time
 * a new RR interval arrives from the BLE Heart Rate Measurement notification.
 *
 * Rendered as a graphical field whose background colour and label show which
 * side of LT1 / LT2 the rider is on right now (see [DfaZone]).
 */
class DfaAlpha1DataType(
    private val bleManager: PolarBleManager,
    extensionId: String,
) : DataTypeImpl(extensionId, TYPE_ID) {

    companion object {
        const val TYPE_ID = "dfa_alpha1"

        // Shown while warming up (DFA α1 needs ~2 min of beats) or with no strap.
        private const val NEUTRAL_COLOR = 0xFF424242.toInt()
    }

    override fun startStream(emitter: Emitter<StreamState>) {
        val cancel = streamFloatWithHold(
            bleManager.dfaAlpha1Flow, bleManager.connectedFlow, dataTypeId, emitter,
        )
        emitter.setCancellable { cancel() }
    }

    override fun startView(context: Context, config: ViewConfig, emitter: ViewEmitter) {
        val valueSizeSp = config.textSize.toFloat()
        if (config.preview) {
            val zone = DfaZone.LT1_TO_LT2
            emitter.updateView(view(context, "0.62", zone.label, zone.color, valueSizeSp))
            emitter.setCancellable {}
            return
        }
        val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
        val job = scope.launch {
            bleManager.dfaAlpha1Flow.collect { alpha ->
                val rv = if (alpha != null) {
                    val zone = classifyDfaZone(alpha.toDouble())
                    view(context, formatAlpha(alpha), zone.label, zone.color, valueSizeSp)
                } else {
                    view(context, "--", "DFA α1", NEUTRAL_COLOR, valueSizeSp)
                }
                emitter.updateView(rv)
            }
        }
        emitter.setCancellable { job.cancel() }
    }

    private fun view(
        context: Context,
        value: String,
        label: String,
        color: Int,
        valueSizeSp: Float,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.dfa_alpha1_field).apply {
        setTextViewText(R.id.dfa_value, value)
        setTextViewTextSize(R.id.dfa_value, TypedValue.COMPLEX_UNIT_SP, valueSizeSp)
        setTextViewText(R.id.dfa_label, label)
        setInt(R.id.dfa_root, "setBackgroundColor", color)
    }

    private fun formatAlpha(alpha: Float): String = String.format(Locale.US, "%.2f", alpha)
}
