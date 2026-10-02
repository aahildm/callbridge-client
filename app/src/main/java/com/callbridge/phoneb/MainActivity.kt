package com.callbridge.phoneb

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
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

    // Nav items: (tab layout, icon view, label view)
    private data class NavItem(val tab: LinearLayout, val icon: TextView, val label: TextView)
    private lateinit var navItems: List<NavItem>
    private var selectedTabIndex = 0

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: return
            when {
                status.startsWith("CONNECTED|") -> {
                    val transport = status.removePrefix("CONNECTED|")
                    setStatusPill("Connected", StatusType.CONNECTED)
                    val transportText = when (transport) {
                        "WiFi"      -> "📶  Wi-Fi  ·  audio + control"
                        "Bluetooth" -> "🔵  Bluetooth  ·  audio + control"
                        else        -> ""
                    }
                    tvTransport.text = transportText
                    tvTransport.visibility = if (transportText.isNotBlank()) View.VISIBLE else View.GONE
                }
                status == "DISCONNECTED" -> {
                    setStatusPill("Disconnected", StatusType.DISCONNECTED)
                    tvTransport.visibility = View.GONE
                }
                else -> {
                    setStatusPill(status.removePrefix("STATUS|"), StatusType.CONNECTING)
                    tvTransport.visibility = View.GONE
                }
            }
        }
    }

    enum class StatusType { CONNECTED, DISCONNECTED, CONNECTING }

    private fun setStatusPill(text: String, type: StatusType) {
        tvStatus.text = text
        val (bg, fg) = when (type) {
            StatusType.CONNECTED    -> getColor(R.color.status_connected_bg) to getColor(R.color.status_connected_text)
            StatusType.DISCONNECTED -> getColor(R.color.status_disconnected_bg) to getColor(R.color.status_disconnected_text)
            StatusType.CONNECTING   -> getColor(R.color.status_connecting_bg) to getColor(R.color.status_connecting_text)
        }
        tvStatus.setTextColor(fg)
        (tvStatus.background as? GradientDrawable)?.setColor(bg)
            ?: tvStatus.setBackgroundColor(bg)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        tvTransport = findViewById(R.id.tvTransport)

        val tabDialer   = findViewById<LinearLayout>(R.id.tabDialer)
        val tabCallLog  = findViewById<LinearLayout>(R.id.tabCallLog)
        val tabContacts = findViewById<LinearLayout>(R.id.tabContacts)
        val tabSms      = findViewById<LinearLayout>(R.id.tabSms)
        val tabSettings = findViewById<LinearLayout>(R.id.tabSettings)

        navItems = listOf(
            NavItem(tabDialer,   findViewById(R.id.navIconDialer),   findViewById(R.id.navLabelDialer)),
            NavItem(tabCallLog,  findViewById(R.id.navIconCalls),    findViewById(R.id.navLabelCalls)),
            NavItem(tabContacts, findViewById(R.id.navIconContacts), findViewById(R.id.navLabelContacts)),
            NavItem(tabSms,      findViewById(R.id.navIconSms),      findViewById(R.id.navLabelSms)),
            NavItem(tabSettings, findViewById(R.id.navIconSettings), findViewById(R.id.navLabelSettings))
        )

        tabDialer.setOnClickListener   { selectTab(0, DialerFragment()) }
        tabCallLog.setOnClickListener  { selectTab(1, CallLogFragment()) }
        tabContacts.setOnClickListener { selectTab(2, ContactsFragment()) }
        tabSms.setOnClickListener      { startActivity(Intent(this, SmsActivity::class.java)) }
        tabSettings.setOnClickListener { selectTab(4, SettingsFragment()) }

        if (savedInstanceState == null) {
            selectTab(0, DialerFragment())
        }

        requestMissingPermissions()
    }

    private fun selectTab(index: Int, fragment: Fragment) {
        selectedTabIndex = index
        val selected   = ContextCompat.getColor(this, R.color.nav_selected)
        val unselected = ContextCompat.getColor(this, R.color.nav_unselected)

        navItems.forEachIndexed { i, item ->
            val color = if (i == index) selected else unselected
            item.icon.setTextColor(color)
            item.label.setTextColor(color)
            item.label.setTypeface(null, if (i == index) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.tabContent, fragment)
            .commit()
    }

    /** Called by CallLogFragment / ContactsFragment when user taps a number. */
    fun goToDialerWithNumber(number: String) {
        val fragment = DialerFragment()
        selectTab(0, fragment)
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
            setStatusPill("Connected", StatusType.CONNECTED)
            val transportText = when (TransportManager.activeTransportName()) {
                "WiFi"      -> "📶  Wi-Fi  ·  audio + control"
                "Bluetooth" -> "🔵  Bluetooth  ·  audio + control"
                else        -> ""
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
