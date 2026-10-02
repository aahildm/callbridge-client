package com.callbridge.phoneb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSIONS = 100

    private val REQUIRED_PERMISSIONS = buildList {
        add(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }.toTypedArray()

    private lateinit var tvStatus: TextView
    private lateinit var tvTransport: TextView
    private var batteryDialogShown = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: return
            when {
                status.startsWith("CONNECTED|") -> {
                    val transport = status.removePrefix("CONNECTED|")
                    tvStatus.text = "✅ Connected to Phone A"
                    val transportText = when (transport) {
                        "WiFi" -> "📶 WiFi  (audio + control)"
                        "Bluetooth" -> "🔵 Bluetooth  (audio + control)"
                        else -> ""
                    }
                    tvTransport.text = transportText
                    tvTransport.visibility = if (transportText.isNotBlank()) View.VISIBLE else View.GONE
                }
                status == "DISCONNECTED" -> {
                    tvStatus.text = "🔴 Disconnected"
                    tvTransport.text = ""
                    tvTransport.visibility = View.GONE
                }
                else -> {
                    tvStatus.text = status
                    tvTransport.text = ""
                    tvTransport.visibility = View.GONE
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTransport = findViewById(R.id.tvTransport)

        val tabDialer = findViewById<android.widget.LinearLayout>(R.id.tabDialer)
        val tabCallLog = findViewById<android.widget.LinearLayout>(R.id.tabCallLog)
        val tabContacts = findViewById<android.widget.LinearLayout>(R.id.tabContacts)
        val tabSms = findViewById<android.widget.LinearLayout>(R.id.tabSms)
        val tabSettings = findViewById<android.widget.LinearLayout>(R.id.tabSettings)

        tabDialer.setOnClickListener { showFragment(DialerFragment()) }
        tabCallLog.setOnClickListener { showFragment(CallLogFragment()) }
        tabContacts.setOnClickListener { showFragment(ContactsFragment()) }
        tabSms.setOnClickListener { startActivity(Intent(this, SmsActivity::class.java)) }
        tabSettings.setOnClickListener { showFragment(SettingsFragment()) }

        // Default tab
        if (savedInstanceState == null) {
            showFragment(DialerFragment())
        }

        requestMissingPermissions()
    }

    private fun showFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.tabContent, fragment)
            .commit()
    }

    /** Called by CallLogFragment when user taps call-back on a log entry. */
    fun goToDialerWithNumber(number: String) {
        val fragment = DialerFragment()
        showFragment(fragment)
        // setNumber runs after the fragment view is created
        supportFragmentManager.executePendingTransactions()
        fragment.setNumber(number)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter("com.callbridge.phoneb.STATUS")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(statusReceiver, filter)
        }

        if (TransportManager.isConnected()) {
            tvStatus.text = "✅ Connected to Phone A"
            val transportText = when (TransportManager.activeTransportName()) {
                "WiFi" -> "📶 WiFi  (audio + control)"
                "Bluetooth" -> "🔵 Bluetooth  (audio + control)"
                else -> ""
            }
            tvTransport.text = transportText
            tvTransport.visibility = if (transportText.isNotBlank()) View.VISIBLE else View.GONE
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

    private fun requestMissingPermissions() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }
}
