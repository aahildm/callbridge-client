package com.callbridge.phoneb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var tvStatus: TextView
    private lateinit var tvTransport: TextView

    // Poll transport state every 2s to keep indicator fresh
    private val statusPoller = object : Runnable {
        override fun run() {
            updateTransportIndicator()
            handler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etIp = findViewById<EditText>(R.id.etPhoneAIp)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnSms = findViewById<Button>(R.id.btnOpenSms)
        tvStatus = findViewById(R.id.tvStatus)
        tvTransport = findViewById(R.id.tvTransport)

        val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("phone_a_ip", ""))

        updateTransportIndicator()

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
            tvTransport.text = "Trying WiFi first..."
        }

        btnSms.setOnClickListener {
            startActivity(Intent(this, SmsActivity::class.java))
        }

        requestPermissionsIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updateTransportIndicator()
        handler.postDelayed(statusPoller, 2000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusPoller)
    }

    private fun updateTransportIndicator() {
        val connected = TransportManager.isConnected()
        val transport = TransportManager.activeTransportName()

        if (connected) {
            tvStatus.text = "✅ Connected to Phone A"
            tvTransport.text = when (transport) {
                "WiFi" -> "📶 Transport: WiFi  (audio + control)"
                "Bluetooth" -> "🔵 Transport: Bluetooth  (audio + control)"
                else -> ""
            }
        } else {
            if (tvStatus.text != "🔄 Connecting to ${
                    getSharedPreferences("callbridge", Context.MODE_PRIVATE)
                        .getString("phone_a_ip", "")
                }...") {
                tvStatus.text = "🔴 Not connected"
            }
            tvTransport.text = ""
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
