package com.callbridge.phoneb

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.appcompat.app.AlertDialog

/**
 * HyperOS/MIUI blocks full-screen call popups from background apps unless
 * "Display pop-up windows while running in the background" is explicitly
 * granted in system settings — this permission has no public API, so we
 * can only detect the ROM and guide the user to the right settings screen.
 */
object PopupPermissionHelper {

    fun showPopupPermissionGuide(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Enable Popup Permission")
            .setMessage(
                "For incoming calls to pop up on screen, HyperOS needs one more permission:\n\n" +
                "1. Tap 'Open Settings' below\n" +
                "2. Find 'Other permissions' or 'More permissions'\n" +
                "3. Enable 'Display pop-up windows while running in the background'\n\n" +
                "Without this, incoming calls will only show as a normal notification, not a full popup."
            )
            .setPositiveButton("Open Settings") { _, _ -> openAppInfoSettings(activity) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun openAppInfoSettings(activity: Activity) {
        try {
            // MIUI-specific permissions screen — falls back to standard app info if unavailable
            val intent = Intent("miui.intent.action.APP_PERM_EDITOR").apply {
                putExtra("extra_pkgname", activity.packageName)
                setClassName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity"
                )
            }
            activity.startActivity(intent)
        } catch (e: Exception) {
            // Fallback: standard Android app settings page
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${activity.packageName}")
            }
            activity.startActivity(intent)
        }
    }
}
