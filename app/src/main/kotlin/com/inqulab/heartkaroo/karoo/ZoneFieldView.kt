package com.inqulab.heartkaroo.karoo

import android.content.Context
import android.widget.RemoteViews
import com.inqulab.heartkaroo.R
import io.hammerhead.karooext.internal.ViewEmitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Builds the shared graphical "coloured zone" field: an auto-sizing value over
 *  a short label on a [color] background. */
fun buildZoneView(context: Context, value: String, label: String, color: Int): RemoteViews =
    RemoteViews(context.packageName, R.layout.zone_field).apply {
        setTextViewText(R.id.zone_value, value)
        setTextViewText(R.id.zone_label, label)
        setInt(R.id.zone_root, "setBackgroundColor", color)
    }

/**
 * Drives a graphical zone field's view from a Float? source: a value renders as
 * [format] text with the [classify]-chosen colour + label; null renders "--" in
 * neutral grey with [idleLabel]. Wires the emitter's cancellable to stop the
 * collector. The source StateFlow lives independently of the view, so showing or
 * hiding the field never resets the underlying accumulation.
 */
fun startZoneView(
    context: Context,
    source: Flow<Float?>,
    emitter: ViewEmitter,
    idleLabel: String,
    format: (Float) -> String,
    classify: (Float) -> Zone,
) {
    val scope = CoroutineScope(Dispatchers.Default.limitedParallelism(1))
    val job = scope.launch {
        source.collect { v ->
            val rv = if (v != null) {
                val z = classify(v)
                buildZoneView(context, format(v), z.label, z.color)
            } else {
                buildZoneView(context, "--", idleLabel, ZoneColors.NEUTRAL)
            }
            emitter.updateView(rv)
        }
    }
    emitter.setCancellable { job.cancel() }
}
