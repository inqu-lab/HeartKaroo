package com.inqulab.heartkaroo.readiness

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.inqulab.heartkaroo.R
import com.inqulab.heartkaroo.aet.AerobicThresholdStore
import com.inqulab.heartkaroo.cadence.OptimalCadenceStore
import com.inqulab.heartkaroo.hrv.PolarBleManager
import com.inqulab.heartkaroo.settings.RiderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "Pre-ride HRV readiness" screen.
 *
 *  1. Asks for BLE permissions.
 *  2. Scans for the first available HR-service device and connects.
 *  3. Collects ~2 minutes of RMSSD samples.
 *  4. Saves the average to ReadinessStore and shows a verdict (go hard /
 *     go easy / normal) against the rolling 7-day baseline.
 */
class ReadinessActivity : AppCompatActivity() {

    private lateinit var bleManager: PolarBleManager
    private lateinit var store: ReadinessStore
    private lateinit var aetStore: AerobicThresholdStore
    private lateinit var cadenceStore: OptimalCadenceStore
    private lateinit var statusView: TextView
    private lateinit var verdictView: TextView
    private lateinit var aetView: TextView
    private lateinit var cadenceView: TextView
    private lateinit var startButton: Button
    private var measurementJob: Job? = null
    private var connectionJob: Job? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) startMeasurement()
        else statusView.text = getString(R.string.readiness_permissions_required)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_readiness)
        bleManager = PolarBleManager.getInstance(applicationContext)
        store = ReadinessStore(applicationContext)
        aetStore = AerobicThresholdStore(applicationContext)
        cadenceStore = OptimalCadenceStore(applicationContext)
        statusView = findViewById(R.id.readiness_status)
        verdictView = findViewById(R.id.readiness_verdict)
        aetView = findViewById(R.id.readiness_aet)
        cadenceView = findViewById(R.id.readiness_cadence)
        startButton = findViewById(R.id.readiness_start)
        startButton.setOnClickListener { ensurePermissionsAndStart() }
        renderBaselineSummary()
        renderAetSummary()
        renderCadenceSummary()
    }

    private fun renderAetSummary() {
        val rolling = aetStore.rollingEstimate()
        val rides = aetStore.entries().size
        aetView.text = if (rolling == null) getString(R.string.readiness_aet_none)
        else getString(R.string.readiness_aet_fmt, rolling.toInt(), rides)
    }

    private fun renderCadenceSummary() {
        val rolling = cadenceStore.rollingEstimate()
        val rides = cadenceStore.entries().size
        cadenceView.text = if (rolling == null) getString(R.string.readiness_cadence_none)
        else getString(R.string.readiness_cadence_fmt, rolling.toInt(), rides)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMeasurement()
    }

    private fun renderBaselineSummary() {
        val recent = store.recent()
        verdictView.text = if (recent.isEmpty()) {
            getString(R.string.readiness_no_history)
        } else {
            val days = recent.size
            val avg = recent.map { it.rmssdMs }.average()
            getString(R.string.readiness_history_fmt, days, avg)
        }
    }

    private fun ensurePermissionsAndStart() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isEmpty()) startMeasurement() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    @Suppress("MissingPermission")
    private fun startMeasurement() {
        // Use the strap paired in Karoo's Sensors section, not a scan that could
        // grab the first (wrong) strap nearby.
        val mac = RiderSettings(applicationContext).pairedStrapMac
        if (mac == null) {
            statusView.text = getString(R.string.readiness_no_paired_strap)
            return
        }
        startButton.isEnabled = false
        verdictView.text = ""
        statusView.text = getString(R.string.readiness_connecting)
        beginConnection(mac)
    }

    private fun beginConnection(mac: String) {
        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                bleManager.connect(mac).collect { /* keep flow alive */ }
            } catch (_: SecurityException) {
                withContext(Dispatchers.Main) {
                    statusView.text = getString(R.string.readiness_permissions_required)
                    startButton.isEnabled = true
                }
            }
        }
        measurementJob = lifecycleScope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) { bleManager.rmssdFlow.first { it > 0f } }
            val startedAt = System.currentTimeMillis()
            val samples = mutableListOf<Float>()
            while (isActive && System.currentTimeMillis() - startedAt < MEASUREMENT_MS) {
                val rmssd = bleManager.rmssdFlow.value
                if (rmssd > 0f) samples.add(rmssd)
                val elapsedSec = (System.currentTimeMillis() - startedAt) / 1000
                val totalSec = MEASUREMENT_MS / 1000
                statusView.text = getString(R.string.readiness_measuring_fmt, elapsedSec, totalSec)
                delay(1_000L)
            }
            stopMeasurement()
            if (samples.size < 10) {
                statusView.text = getString(R.string.readiness_too_few_samples)
                startButton.isEnabled = true
                return@launch
            }
            val avg = samples.average().toFloat()
            store.record(System.currentTimeMillis(), avg)
            renderVerdict(avg)
            startButton.isEnabled = true
        }
    }

    private fun renderVerdict(todayRmssd: Float) {
        val status = store.statusFor(todayRmssd)
        val label = when (status.label) {
            ReadinessStore.Verdict.GO_HARD -> getString(R.string.readiness_verdict_hard)
            ReadinessStore.Verdict.GO_EASY -> getString(R.string.readiness_verdict_easy)
            ReadinessStore.Verdict.NORMAL -> getString(R.string.readiness_verdict_normal)
            ReadinessStore.Verdict.NO_BASELINE -> getString(R.string.readiness_verdict_no_baseline)
        }
        statusView.text = getString(R.string.readiness_today_fmt, todayRmssd)
        verdictView.text = if (status.label == ReadinessStore.Verdict.NO_BASELINE) label
        else getString(R.string.readiness_verdict_with_z_fmt, label, status.zScore)
    }

    private fun stopMeasurement() {
        measurementJob?.cancel(); measurementJob = null
        connectionJob?.cancel(); connectionJob = null
        // Don't disconnect: the strap link is a process-wide singleton shared with
        // the extension service, which needs it during the ride. Closing this
        // screen just stops our collectors; the link stays up.
    }

    private companion object {
        const val MEASUREMENT_MS = 2L * 60 * 1000
    }
}
