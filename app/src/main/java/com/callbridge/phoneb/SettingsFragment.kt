package com.callbridge.phoneb

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
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

        val btnConnect       = view.findViewById<Button>(R.id.btnConnect)
        val btnBattery       = view.findViewById<Button>(R.id.btnBatteryFix)
        val tvPairedDevice   = view.findViewById<TextView>(R.id.tvPairedDevice)
        val btnRestartApp    = view.findViewById<Button>(R.id.btnRestartApp)
        val btnPopupPermission = view.findViewById<Button>(R.id.btnPopupPermission)

        updateBatteryButton(btnBattery)
        updatePairedDeviceInfo(tvPairedDevice)

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

        btnRestartApp.setOnClickListener {
            Toast.makeText(requireContext(), "Restarting...", Toast.LENGTH_SHORT).show()
            RestartHelper.restartApp(requireActivity())
        }
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
        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()
            val device = adapter?.bondedDevices?.firstOrNull()
            tv.text = if (device != null) {
                "✅ Paired with: ${device.name} — ready to connect"
            } else {
                "⚠️ No paired Bluetooth device found.\nPair with Phone A in system Bluetooth settings first."
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
