package com.inqulab.heartkaroo

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.inqulab.heartkaroo.readiness.ReadinessActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.open_readiness).setOnClickListener {
            startActivity(Intent(this, ReadinessActivity::class.java))
        }
    }
}
