package com.callbridge.phoneb

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log

object TransportManager {

    private val TAG = "CallBridge-Transport"
    private const val WIFI_TIMEOUT_MS = 15000L

    enum class Active { NONE, WIFI, BLUETOOTH }

    internal var active = Active.NONE
        private set

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
        SocketClient.resume()
        BluetoothClient.disconnect()

        Log.d(TAG, "Trying WiFi to $ip first")
        SocketClient.connect(ip)

        fallbackRunnable = Runnable {
            if (active != Active.WIFI) {
                Log.d(TAG, "WiFi timeout — falling back to Bluetooth")
                onEvent?.invoke("FALLBACK_BLUETOOTH")
                SocketClient.disconnect()
                val ok = BluetoothClient.connect()
                if (!ok) onEvent?.invoke("DISCONNECTED")
            }
        }
        handler.postDelayed(fallbackRunnable!!, WIFI_TIMEOUT_MS)
    }

    /** Manually forces WiFi, cancelling any Bluetooth attempt and re-enabling
     *  WiFi's reconnect logic (which forceBluetooth had suspended). */
    fun forceWifi(ip: String) {
        cancelFallback()
        BluetoothClient.disconnect()
        active = Active.NONE
        SocketClient.resume()
        Log.d(TAG, "Forcing WiFi transport to $ip")
        onEvent?.invoke("STATUS|Forcing WiFi...")
        SocketClient.connect(ip)
    }

    /** Manually forces Bluetooth. Suspends WiFi entirely first — this is the
     *  key fix: without suspend(), a WiFi reconnect timer scheduled before
     *  this call could still fire later and silently pull the connection
     *  back to WiFi, overwriting the Bluetooth status. */
    fun forceBluetooth() {
        cancelFallback()
        SocketClient.suspend()
        active = Active.NONE
        Log.d(TAG, "Forcing Bluetooth transport")
        onEvent?.invoke("STATUS|Forcing Bluetooth...")
        val ok = BluetoothClient.connect()
        if (!ok) {
            // connect() already emitted a specific STATUS reason via onEvent
            onEvent?.invoke("DISCONNECTED")
        }
    }

    private fun onTransportEvent(from: Active, event: String) {
        if (event == "CONNECTED") {
            active = from
            cancelFallback()
            Log.d(TAG, "Connected via $from")
        }
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

    fun isConnected() = when (active) {
        Active.WIFI -> SocketClient.isConnected()
        Active.BLUETOOTH -> BluetoothClient.isConnected()
        Active.NONE -> false
    }

    fun activeTransportName() = when (active) {
        Active.WIFI -> "WiFi"
        Active.BLUETOOTH -> "Bluetooth"
        Active.NONE -> "None"
    }

    fun isBluetoothActive() = active == Active.BLUETOOTH

    fun disconnect() {
        cancelFallback()
        SocketClient.disconnect()
        BluetoothClient.disconnect()
        active = Active.NONE
    }
}
