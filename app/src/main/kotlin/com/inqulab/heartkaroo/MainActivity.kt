package com.inqulab.heartkaroo

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.inqulab.heartkaroo.hrv.PolarBleManager
import com.inqulab.heartkaroo.readiness.ReadinessActivity
import com.inqulab.heartkaroo.settings.RiderSettings
import com.inqulab.heartkaroo.settings.SettingsActivity
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* Granting here is enough — Karoo's Sensors scan can now find the strap. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Request BLE permissions up front so the extension's startScan (driven by
        // Karoo's Sensors section, a Service that can't prompt) can find the strap.
        requestBlePermissionsIfNeeded()
        findViewById<Button>(R.id.open_readiness).setOnClickListener {
            startActivity(Intent(this, ReadinessActivity::class.java))
        }
        findViewById<Button>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        watchStrapStatus()
    }

    /** Live "which strap am I connected to?" line, so the rider can confirm the
     *  RIGHT strap is linked when several are nearby. The BLE link is process-wide
     *  (PolarBleManager singleton, driven by the extension service), so this
     *  screen only observes it. */
    private fun watchStrapStatus() {
        val statusView = findViewById<TextView>(R.id.strap_status)
        val settings = RiderSettings(applicationContext)
        val bleManager = PolarBleManager.getInstance(applicationContext)
        lifecycleScope.launch {
            bleManager.connectedDeviceNameFlow.collect { name ->
                val pairedMac = settings.pairedStrapMac
                statusView.text = when {
                    name != null -> getString(R.string.strap_status_connected, name)
                    pairedMac != null -> getString(R.string.strap_status_searching, pairedMac)
                    else -> getString(R.string.strap_status_none)
                }
            }
        }
    }

    private fun requestBlePermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!granted(Manifest.permission.BLUETOOTH_SCAN)) needed.add(Manifest.permission.BLUETOOTH_SCAN)
            if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }

    private fun granted(perm: String): Boolean =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
}
