package com.callbridge.phoneb

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnConnect         = view.findViewById<Button>(R.id.btnConnect)
        val btnBattery         = view.findViewById<Button>(R.id.btnBatteryFix)
        val tvPairedDevice     = view.findViewById<TextView>(R.id.tvPairedDevice)
        val btnRestartApp      = view.findViewById<Button>(R.id.btnRestartApp)
        val btnPopupPermission = view.findViewById<Button>(R.id.btnPopupPermission)

        updateBatteryButton(btnBattery)
        updatePairedDeviceInfo(tvPairedDevice)

        // Wire picker so BluetoothClient can ask us to show a device list
        BluetoothClient.onPickDevice = { devices ->
            requireActivity().runOnUiThread {
                showDevicePicker(devices, tvPairedDevice)
            }
        }

        btnConnect.setOnClickListener {
            startClientService()
            TransportManager.connect()
            Toast.makeText(requireContext(), "Connecting via Bluetooth...", Toast.LENGTH_SHORT).show()
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

        val btnUplinkMode = view.findViewById<Button>(R.id.btnUplinkMode)
        val prefs = requireContext().getSharedPreferences("callbridge", android.content.Context.MODE_PRIVATE)
        fun renderUplink() {
            val mode = prefs.getString("uplink_mode", "speaker")
            btnUplinkMode.text = if (mode == "tx") "🎙 Voice to caller: Direct (tap for Speaker)"
                                 else "🎙 Voice to caller: Speaker (tap for Direct)"
        }
        renderUplink()
        TransportManager.send("GET_UPLINK_MODE")
        btnUplinkMode.setOnClickListener {
            if (!TransportManager.isConnected()) {
                Toast.makeText(requireContext(), "Not connected", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val next = if (prefs.getString("uplink_mode", "speaker") == "tx") "speaker" else "tx"
            prefs.edit().putString("uplink_mode", next).apply()
            TransportManager.send("UPLINK_MODE|$next")
            renderUplink()
            Toast.makeText(requireContext(), "Switched — applies instantly, even mid-call", Toast.LENGTH_SHORT).show()
        }

        btnRestartApp.setOnClickListener {
            Toast.makeText(requireContext(), "Restarting...", Toast.LENGTH_SHORT).show()
            RestartHelper.restartApp(requireActivity())
        }
    }

    @SuppressLint("MissingPermission")
    private fun showDevicePicker(devices: List<BluetoothDevice>, tvPairedDevice: TextView) {
        val names = devices.map { it.name ?: it.address }.toTypedArray()
        AlertDialog.Builder(requireContext())
            .setTitle("Select Phone A (server device)")
            .setItems(names) { _, idx ->
                val chosen = devices[idx]
                BluetoothClient.saveDeviceName(chosen.name ?: chosen.address)
                updatePairedDeviceInfo(tvPairedDevice)
                // Now actually connect to the chosen device
                startClientService()
                TransportManager.connect()
                Toast.makeText(requireContext(),
                    "Connecting to ${chosen.name}...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startClientService() {
        val serviceIntent = Intent(requireContext(), ClientService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(serviceIntent)
        } else {
            requireContext().startService(serviceIntent)
        }
    }

    @SuppressLint("MissingPermission")
    private fun updatePairedDeviceInfo(tv: TextView) {
        val savedName = BluetoothClient.getSavedDeviceName()
        tv.text = if (savedName != null) {
            "✅ Server device: $savedName — tap Connect to link"
        } else {
            "⚠️ No server device selected — tap Connect to pick one from your paired devices"
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
