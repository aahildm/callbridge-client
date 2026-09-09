package com.callbridge.phoneb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

    private lateinit var tvStatus: TextView
    private lateinit var tvTransport: TextView
    private var batteryDialogShown = false

    // Receives real-time status from ClientService
    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: return
            when {
                status.startsWith("CONNECTED|") -> {
                    val transport = status.removePrefix("CONNECTED|")
                    tvStatus.text = "✅ Connected to Phone A"
                    tvTransport.text = when (transport) {
                        "WiFi" -> "📶 WiFi  (audio + control)"
                        "Bluetooth" -> "🔵 Bluetooth  (audio + control)"
                        else -> ""
                    }
                }
                status == "DISCONNECTED" -> {
                    tvStatus.text = "🔴 Disconnected"
                    tvTransport.text = ""
                }
                else -> {
                    // Show raw status message — e.g. error details, retry info
                    tvStatus.text = status
                    tvTransport.text = ""
                }
            }
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
            tvStatus.text = "🔄 Starting..."
            tvTransport.text = ""
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
        updateBatteryButton(findViewById(R.id.btnBatteryFix))

        val filter = IntentFilter("com.callbridge.phoneb.STATUS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }

        // Reflect current state on resume
        if (TransportManager.isConnected()) {
            tvStatus.text = "✅ Connected to Phone A"
            tvTransport.text = when (TransportManager.activeTransportName()) {
                "WiFi" -> "📶 WiFi  (audio + control)"
                "Bluetooth" -> "🔵 Bluetooth  (audio + control)"
                else -> ""
            }
        }

        if (!batteryDialogShown && PermissionHelper.isBatteryOptimized(this)) {
            batteryDialogShown = true
            PermissionHelper.showBatteryDialog(this)
        }
    }

    override fun onPause() {
        super.onPause()
        try { unregisterReceiver(statusReceiver) } catch (_: Exception) {}
    }

    private fun updateBatteryButton(btn: Button) {
        btn.text = when {
            PermissionHelper.isBatteryOptimized(this) -> "⚠️ Fix Battery Optimization"
            PermissionHelper.isMiui() -> "📱 HyperOS Setup Guide"
            else -> "✅ Battery OK"
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
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val denied = permissions.zip(grantResults.toList())
                .filter { it.second != PackageManager.PERMISSION_GRANTED }
                .map { it.first.substringAfterLast(".") }
            if (denied.isNotEmpty()) {
                tvStatus.text = "⚠️ Missing: ${denied.joinToString(", ")}"
            }
        }
    }
}
