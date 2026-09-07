package com.callbridge.phoneb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        // POST_NOTIFICATIONS needed on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        // BLUETOOTH_CONNECT needed on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etIp = findViewById<EditText>(R.id.etPhoneAIp)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnSms = findViewById<Button>(R.id.btnOpenSms)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("phone_a_ip", "")
        etIp.setText(savedIp)

        tvStatus.text = if (TransportManager.isConnected()) "✅ Connected" else "🔴 Not connected"

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                tvStatus.text = "⚠️ Enter Phone A's IP address"
                return@setOnClickListener
            }
            prefs.edit().putString("phone_a_ip", ip).apply()

            val serviceIntent = Intent(this, ClientService::class.java)
                .putExtra("phone_a_ip", ip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
            tvStatus.text = "🔄 Connecting to $ip..."
        }

        btnSms.setOnClickListener {
            startActivity(Intent(this, SmsActivity::class.java))
        }

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        val tvStatus = findViewById<TextView>(R.id.tvStatus)
        // Don't overwrite "Connecting..." with "Not connected" right after tapping Connect
        if (TransportManager.isConnected()) {
            tvStatus.text = "✅ Connected to Phone A"
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missing = PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
        }
    }
}
