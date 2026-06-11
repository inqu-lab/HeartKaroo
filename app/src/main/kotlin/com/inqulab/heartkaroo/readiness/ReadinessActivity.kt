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
import com.inqulab.heartkaroo.power.EftpStore
import com.inqulab.heartkaroo.settings.RiderSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
    private lateinit var eftpStore: EftpStore
    private lateinit var statusView: TextView
    private lateinit var verdictView: TextView
    private lateinit var aetView: TextView
    private lateinit var cadenceView: TextView
    private lateinit var eftpView: TextView
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
        eftpStore = EftpStore(applicationContext)
        statusView = findViewById(R.id.readiness_status)
        verdictView = findViewById(R.id.readiness_verdict)
        aetView = findViewById(R.id.readiness_aet)
        cadenceView = findViewById(R.id.readiness_cadence)
        eftpView = findViewById(R.id.readiness_eftp)
        startButton = findViewById(R.id.readiness_start)
        startButton.setOnClickListener { ensurePermissionsAndStart() }
        renderBaselineSummary()
        renderAetSummary()
        renderCadenceSummary()
        renderEftpSummary()
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

    private fun renderEftpSummary() {
        val best = eftpStore.rollingBest()
        val rides = eftpStore.entries().size
        eftpView.text = if (best == null) getString(R.string.readiness_eftp_none)
        else getString(R.string.readiness_eftp_fmt, best.toInt(), rides)
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
        // Name the strap being connected so the rider can spot a wrong (e.g. a
        // ride mate's) strap before the 2-minute reading starts.
        statusView.text = getString(R.string.readiness_connecting_fmt, mac)
        beginConnection(mac)
    }

    private fun beginConnection(mac: String) {
        connectionJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                bleManager.connect(mac).collect { /* keep flow alive */ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SecurityException = missing BLE permission; anything else is the
                // BLE stack failing (Bluetooth off, adapter unavailable). Show a
                // status instead of crashing the process.
                withContext(Dispatchers.Main) {
                    statusView.text =
                        if (e is SecurityException) getString(R.string.readiness_permissions_required)
                        else getString(R.string.readiness_connection_error)
                    startButton.isEnabled = true
                    measurementJob?.cancel(); measurementJob = null
                }
            }
        }
        measurementJob = lifecycleScope.launch(Dispatchers.Main) {
            // Don't wait forever with the button disabled when the strap isn't in
            // range — the link is manager-owned, so a late connect still lands and
            // the next Start succeeds immediately.
            val name = withContext(Dispatchers.IO) {
                withTimeoutOrNull(CONNECT_TIMEOUT_MS) {
                    bleManager.connectedDeviceNameFlow.filterNotNull().first()
                }
            }
            if (name == null) {
                statusView.text = getString(R.string.readiness_strap_not_found)
                startButton.isEnabled = true
                connectionJob?.cancel(); connectionJob = null
                return@launch
            }
            statusView.text = getString(R.string.readiness_connected_fmt, name)
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
            // Verdict BEFORE recording: today's reading must be z-scored against
            // the prior baseline, not a baseline it is itself part of (which pulls
            // the mean toward today and biases verdicts toward NORMAL).
            renderVerdict(avg)
            store.record(System.currentTimeMillis(), avg)
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
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
