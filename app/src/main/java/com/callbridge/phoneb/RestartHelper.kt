package com.callbridge.phoneb

import android.app.Activity
import android.content.Intent
import android.os.Process

/**
 * Force-closes and relaunches the app cleanly. Needed because after code
 * updates or connection state changes, stale singletons (TransportManager,
 * SocketClient, etc.) can hold onto dead sockets that a normal "reconnect"
 * button can't fully clear — only a real process restart guarantees a
 * fresh state.
 */
object RestartHelper {

    fun restartApp(activity: Activity) {
        val packageManager = activity.packageManager
        val intent = packageManager.getLaunchIntentForPackage(activity.packageName)
        val mainIntent = Intent.makeRestartActivityTask(intent?.component)
        activity.startActivity(mainIntent)
        // Kill the current process entirely — stops the foreground service too,
        // ensuring no stale socket/thread survives into the new process.
        Runtime.getRuntime().exit(0)
    }
}
