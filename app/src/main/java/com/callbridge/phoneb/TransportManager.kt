package com.callbridge.phoneb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Picks a transport to reach Phone A: tries WiFi (SocketClient) first, and
 * if it hasn't connected within WIFI_TIMEOUT_MS, automatically falls back
 * to Bluetooth (BluetoothClient) against a paired device. Everything else
 * (ClientService, IncomingCallActivity, etc.) just calls TransportManager
 * the same way it used to call SocketClient directly.
 */
object TransportManager {

    private val TAG = "CallBridge-Transport"
    private const val WIFI_TIMEOUT_MS = 6000L

    enum class Active { NONE, WIFI, BLUETOOTH }

    private var active = Active.NONE
    private val handler = Handler(Looper.getMainLooper())
    private var fallbackRunnable: Runnable? = null

    var onEvent: ((String) -> Unit)? = null

    fun init(context: Context) {
        SocketClient.init(context)
        BluetoothClient.init(context)

        SocketClient.onEvent = { event -> onTransportEvent(Active.WIFI, event) }
        BluetoothClient.onEvent = { event -> onTransportEvent(Active.BLUETOOTH, event) }
    }

    fun connect(ip: String) {
        cancelFallback()
        active = Active.NONE
        BluetoothClient.disconnect()

        Log.d(TAG, "Trying WiFi to $ip first")
        SocketClient.connect(ip)

        fallbackRunnable = Runnable {
            if (active != Active.WIFI) {
                Log.d(TAG, "WiFi didn't connect in time — falling back to Bluetooth")
                onEvent?.invoke("FALLBACK_BLUETOOTH")
                SocketClient.disconnect()
                val ok = BluetoothClient.connect()
                if (!ok) {
                    onEvent?.invoke("DISCONNECTED")
                }
            }
        }
        handler.postDelayed(fallbackRunnable!!, WIFI_TIMEOUT_MS)
    }

    private fun onTransportEvent(from: Active, event: String) {
        if (event == "CONNECTED") {
            active = from
            cancelFallback()
            Log.d(TAG, "Connected via $from")
        }
        // Only forward events from whichever transport is currently active,
        // once one has been chosen — avoids stale WiFi retries leaking
        // through after we've already switched to Bluetooth (or vice versa).
        if (active == Active.NONE || active == from) {
            onEvent?.invoke(event)
        }
    }

    private fun cancelFallback() {
        fallbackRunnable?.let { handler.removeCallbacks(it) }
        fallbackRunnable = null
    }

    fun send(message: String) = when (active) {
        Active.BLUETOOTH -> BluetoothClient.send(message)
        else -> SocketClient.send(message)
    }

    fun answer() = send("ANSWER")
    fun reject() = send("REJECT")
    fun hangup() = send("HANGUP")
    fun sendSms(number: String, body: String) = send("SMS_SEND|$number|$body")

    fun isConnected(): Boolean = when (active) {
        Active.WIFI -> SocketClient.isConnected()
        Active.BLUETOOTH -> BluetoothClient.isConnected()
        Active.NONE -> false
    }

    fun activeTransportName(): String = when (active) {
        Active.WIFI -> "WiFi"
        Active.BLUETOOTH -> "Bluetooth"
        Active.NONE -> "None"
    }

    fun disconnect() {
        cancelFallback()
        SocketClient.disconnect()
        BluetoothClient.disconnect()
        active = Active.NONE
    }
}
