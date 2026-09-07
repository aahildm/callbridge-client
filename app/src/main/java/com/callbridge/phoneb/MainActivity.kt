package com.callbridge.phoneb

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSIONS = 100

    private val REQUIRED_PERMISSIONS = buildList {
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
    private var batteryDialogShown = false

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
        val btnBattery = findViewById<Button>(R.id.btnBatteryFix)
        tvStatus = findViewById(R.id.tvStatus)
        tvTransport = findViewById(R.id.tvTransport)

        val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("phone_a_ip", ""))

        updateTransportIndicator()
        updateBatteryButton(btnBattery)

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

        btnBattery.setOnClickListener {
            if (PermissionHelper.isBatteryOptimized(this)) {
                PermissionHelper.showBatteryDialog(this)
            } else if (PermissionHelper.isMiui()) {
                PermissionHelper.showHyperOsGuide(this)
            }
        }

        requestMissingPermissions()
    }

    override fun onResume() {
        super.onResume()
        updateTransportIndicator()
        updateBatteryButton(findViewById(R.id.btnBatteryFix))
        handler.postDelayed(statusPoller, 2000)

        // Auto-prompt battery dialog once if needed
        if (!batteryDialogShown && PermissionHelper.isBatteryOptimized(this)) {
            batteryDialogShown = true
            PermissionHelper.showBatteryDialog(this)
        }
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(statusPoller)
    }

    private fun updateBatteryButton(btn: Button) {
        val optimized = PermissionHelper.isBatteryOptimized(this)
        val isMiui = PermissionHelper.isMiui()
        btn.text = when {
            optimized -> "⚠️ Fix Battery Optimization"
            isMiui -> "📱 HyperOS Setup Guide"
            else -> "✅ Battery Optimization OK"
        }
    }

    private fun updateTransportIndicator() {
        val connected = TransportManager.isConnected()
        val transport = TransportManager.activeTransportName()
        if (connected) {
            tvStatus.text = "✅ Connected to Phone A"
            tvTransport.text = when (transport) {
                "WiFi" -> "📶 WiFi  (audio + control)"
                "Bluetooth" -> "🔵 Bluetooth  (audio + control)"
                else -> ""
            }
        } else {
            val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            val savedIp = prefs.getString("phone_a_ip", "")
            if (tvStatus.text != "🔄 Connecting to $savedIp...") {
                tvStatus.text = "🔴 Not connected"
            }
            tvTransport.text = ""
        }
    }

    private fun requestMissingPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val denied = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first }
            if (denied.isNotEmpty()) {
                tvStatus.text = "⚠️ Missing permissions — app may not work"
            }
        }
    }
}
