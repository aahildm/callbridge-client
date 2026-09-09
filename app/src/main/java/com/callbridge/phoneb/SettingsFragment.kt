package com.callbridge.phoneb

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etIp = view.findViewById<EditText>(R.id.etPhoneAIp)
        val btnConnect = view.findViewById<Button>(R.id.btnConnect)
        val btnBattery = view.findViewById<Button>(R.id.btnBatteryFix)

        val prefs = requireContext().getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        etIp.setText(prefs.getString("phone_a_ip", ""))

        updateBatteryButton(btnBattery)

        btnConnect.setOnClickListener {
            val ip = etIp.text.toString().trim()
            if (ip.isEmpty()) return@setOnClickListener
            prefs.edit().putString("phone_a_ip", ip).apply()

            val serviceIntent = Intent(requireContext(), ClientService::class.java)
                .putExtra("phone_a_ip", ip)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                requireContext().startForegroundService(serviceIntent)
            } else {
                requireContext().startService(serviceIntent)
            }
        }

        btnBattery.setOnClickListener {
            val act = requireActivity()
            if (PermissionHelper.isBatteryOptimized(requireContext())) {
                PermissionHelper.showBatteryDialog(act)
            } else if (PermissionHelper.isMiui()) {
                PermissionHelper.showHyperOsGuide(act)
            }
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
    }
}
