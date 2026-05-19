package com.inqulab.heartkaroo.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.inqulab.heartkaroo.R

/**
 * Rider-settings screen: FTP, critical power, W′, HRmax, weight.
 *
 * The values feed Intensity Factor / TSS (FTP), W′ balance (CP, W′),
 * heart-rate-zone derived fields (HRmax), and any future power-to-weight
 * fields (weight).
 *
 * Each input is range-validated on save; invalid input cancels the save
 * and shows a toast. "Reset to defaults" restores the in-code defaults
 * without saving — the user still has to press Save to persist.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var settings: RiderSettings
    private lateinit var ftpField: EditText
    private lateinit var cpField: EditText
    private lateinit var wPrimeField: EditText
    private lateinit var hrMaxField: EditText
    private lateinit var weightField: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        settings = RiderSettings(applicationContext)
        ftpField = findViewById(R.id.settings_ftp)
        cpField = findViewById(R.id.settings_cp)
        wPrimeField = findViewById(R.id.settings_wprime)
        hrMaxField = findViewById(R.id.settings_hrmax)
        weightField = findViewById(R.id.settings_weight)

        populateFromStore()

        findViewById<Button>(R.id.settings_save).setOnClickListener { onSave() }
        findViewById<Button>(R.id.settings_reset).setOnClickListener { onReset() }
    }

    private fun populateFromStore() {
        ftpField.setText(settings.ftpW.toString())
        cpField.setText(settings.criticalPowerW.toString())
        wPrimeField.setText(settings.wPrimeJ.toString())
        hrMaxField.setText(settings.hrMax.toString())
        weightField.setText(settings.weightKg.toString())
    }

    private fun onReset() {
        ftpField.setText(RiderSettings.DEFAULT_FTP.toString())
        cpField.setText(RiderSettings.DEFAULT_CP.toString())
        wPrimeField.setText(RiderSettings.DEFAULT_WPRIME.toString())
        hrMaxField.setText(RiderSettings.DEFAULT_HRMAX.toString())
        weightField.setText(RiderSettings.DEFAULT_WEIGHT.toString())
        Toast.makeText(this, R.string.settings_reset_toast, Toast.LENGTH_SHORT).show()
    }

    private fun onSave() {
        val ftp = parseIntInRange(ftpField, 50, 600, R.string.settings_err_ftp) ?: return
        val cp = parseIntInRange(cpField, 50, 600, R.string.settings_err_cp) ?: return
        val wPrime = parseIntInRange(wPrimeField, 5_000, 40_000, R.string.settings_err_wprime) ?: return
        val hrMax = parseIntInRange(hrMaxField, 100, 230, R.string.settings_err_hrmax) ?: return
        val weight = parseFloatInRange(weightField, 30f, 200f, R.string.settings_err_weight) ?: return

        settings.ftpW = ftp
        settings.criticalPowerW = cp
        settings.wPrimeJ = wPrime
        settings.hrMax = hrMax
        settings.weightKg = weight
        Toast.makeText(this, R.string.settings_saved_toast, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun parseIntInRange(field: EditText, min: Int, max: Int, errRes: Int): Int? {
        val v = field.text.toString().trim().toIntOrNull()
        if (v == null || v !in min..max) {
            Toast.makeText(this, getString(errRes, min, max), Toast.LENGTH_LONG).show()
            field.requestFocus()
            return null
        }
        return v
    }

    private fun parseFloatInRange(field: EditText, min: Float, max: Float, errRes: Int): Float? {
        val v = field.text.toString().trim().toFloatOrNull()
        if (v == null || v < min || v > max) {
            Toast.makeText(this, getString(errRes, min, max), Toast.LENGTH_LONG).show()
            field.requestFocus()
            return null
        }
        return v
    }
}
