package com.callbridge.phoneb

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

/**
 * Centralized permission + battery optimization helper for Phone B.
 * Call checkAndGuide() from MainActivity.onResume().
 */
object PermissionHelper {

    fun isBatteryOptimized(context: Context): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        return !pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Opens system dialog to request battery optimization exemption. */
    fun requestBatteryExemption(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            try {
                activity.startActivity(intent)
            } catch (e: Exception) {
                // Some ROMs block this intent — fall back to app settings
                openAppSettings(activity)
            }
        }
    }

    /** Opens app's system settings page (manual fallback). */
    fun openAppSettings(activity: Activity) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${activity.packageName}")
        }
        activity.startActivity(intent)
    }

    /** Shows a dialog explaining why battery exemption is needed, then requests it. */
    fun showBatteryDialog(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Keep CallBridge Running")
            .setMessage(
                "Your phone's battery optimization will kill CallBridge in the background, " +
                "causing you to miss calls.\n\n" +
                "Tap 'Allow' on the next screen to exempt CallBridge from battery optimization."
            )
            .setPositiveButton("Fix Now") { _, _ -> requestBatteryExemption(activity) }
            .setNegativeButton("Later", null)
            .setCancelable(false)
            .show()
    }

    /** Shows ROM-specific instructions for HyperOS / MIUI autostart. */
    fun showHyperOsGuide(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("HyperOS / MIUI Extra Steps")
            .setMessage(
                "On Xiaomi/POCO devices you also need to:\n\n" +
                "1. Settings → Apps → Manage apps → CallBridge\n" +
                "   → Battery saver → No restrictions\n\n" +
                "2. Settings → Apps → Manage apps → CallBridge\n" +
                "   → Autostart → ON\n\n" +
                "3. Long-press CallBridge in recents → Lock it\n\n" +
                "These steps prevent HyperOS from killing the app."
            )
            .setPositiveButton("Open App Settings") { _, _ -> openAppSettings(activity) }
            .setNegativeButton("Got it", null)
            .show()
    }

    /** Detects if running on MIUI / HyperOS. */
    fun isMiui(): Boolean {
        return try {
            val prop = System.getProperty("ro.miui.ui.version.name")
            !prop.isNullOrEmpty()
        } catch (e: Exception) {
            false
        }
    }
}
