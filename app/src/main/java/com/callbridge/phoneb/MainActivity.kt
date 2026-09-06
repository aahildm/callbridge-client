package com.callbridge.phoneb

import android.Manifest
import android.content.Context
import android.content.Intent
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

    private val PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.POST_NOTIFICATIONS
        )
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val etIp = findViewById<EditText>(R.id.etPhoneAIp)
        val btnConnect = findViewById<Button>(R.id.btnConnect)
        val btnSms = findViewById<Button>(R.id.btnOpenSms)
        val tvStatus = findViewById<TextView>(R.id.tvStatus)

        // Load saved IP
        val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        val savedIp = prefs.getString("phone_a_ip", "")
        etIp.setText(savedIp)

        tvStatus.text = if (SocketClient.isConnected()) "✅ Connected" else "🔴 Not connected"

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
        tvStatus.text = if (SocketClient.isConnected()) "✅ Connected to Phone A" else "🔴 Not connected"
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
