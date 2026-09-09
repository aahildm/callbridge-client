package com.callbridge.phoneb

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etIp = view.findViewById<EditText>(R.id.etPhoneAIp)
        val btnConnect = view.findViewById<Button>(R.id.btnConnect)
        val btnBattery = view.findViewById<Button>(R.id.btnBatteryFix)
        val btnForceWifi = view.findViewById<Button>(R.id.btnForceWifi)
        val btnForceBluetooth = view.findViewById<Button>(R.id.btnForceBluetooth)
        val tvPairedDevice = view.findViewById<TextView>(R.id.tvPairedDevice)
        val btnRestartApp = view.findViewById<Button>(R.id.btnRestartApp)
        val btnPopupPermission = view.findViewById<Button>(R.id.btnPopupPermission)

        val prefs = requireContext().getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("phone_a_ip", ""))

        updateBatteryButton(btnBattery)
        updatePairedDeviceInfo(tvPairedDevice)

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) return@setOnClickListener
            prefs.edit().putString("phone_a_ip", ip).apply()
            startClientService(ip)
        }

        btnForceWifi.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) {
                Toast.makeText(requireContext(), "Enter Phone A's IP first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            ensureServiceRunning(ip)
            TransportManager.forceWifi(ip)
        }

        btnForceBluetooth.setOnClickListener {
            ensureServiceRunning(etIp.text.toString().trim())
            TransportManager.forceBluetooth()
        }

        btnBattery.setOnClickListener {
            val act = requireActivity()
            if (PermissionHelper.isBatteryOptimized(requireContext())) {
                PermissionHelper.showBatteryDialog(act)
            } else if (PermissionHelper.isMiui()) {
                PermissionHelper.showHyperOsGuide(act)
            }
        }

        btnPopupPermission.setOnClickListener {
            PopupPermissionHelper.showPopupPermissionGuide(requireActivity())
        }

        btnRestartApp.setOnClickListener {
            Toast.makeText(requireContext(), "Restarting...", Toast.LENGTH_SHORT).show()
            RestartHelper.restartApp(requireActivity())
        }
    }

    private fun startClientService(ip: String) {
        val serviceIntent = Intent(requireContext(), ClientService::class.java)
            .putExtra("phone_a_ip", ip)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }

    private fun ensureServiceRunning(ip: String) {
        if (ip.isNotEmpty()) {
            val prefs = requireContext().getSharedPreferences("callbridge", Context.MODE_PRIVATE)
            if (prefs.getString("phone_a_ip", "").isNullOrEmpty()) {
                prefs.edit().putString("phone_a_ip", ip).apply()
            }
        }
        startClientService(ip)
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDeviceInfo(tv: TextView) {
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = adapter?.bondedDevices?.firstOrNull()
            tv.text = if (device != null) {
                "Paired Bluetooth device: ${device.name}"
            } else {
                "⚠️ No paired Bluetooth device — pair with Phone A first for Bluetooth fallback"
            }
        } catch (e: Exception) {
            tv.text = "Bluetooth status unavailable"
        }
    }

    private fun updateBatteryButton(btn: Button) {
        btn.text = when {
            PermissionHelper.isBatteryOptimized(requireContext()) -> "⚠️ Fix Battery Optimization"
            PermissionHelper.isMiui() -> "📱 HyperOS Setup Guide"
            else -> "✅ Battery OK"
        }
    }

    override fun onResume() {
        super.onResume()
        view?.findViewById<Button>(R.id.btnBatteryFix)?.let { updateBatteryButton(it) }
        view?.findViewById<TextView>(R.id.tvPairedDevice)?.let { updatePairedDeviceInfo(it) }
    }
}
