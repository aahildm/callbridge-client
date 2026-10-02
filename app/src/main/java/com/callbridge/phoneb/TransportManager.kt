package com.callbridge.phoneb

import android.content.Context
import android.util.Log

object TransportManager {
    private val TAG = "CallBridge-Transport"
    var onEvent: ((String) -> Unit)? = null

    fun init(context: Context) {
        BluetoothClient.init(context)
        BluetoothClient.onEvent = { event -> onEvent?.invoke(event) }
    }

    fun connect(ip: String = "") {
        Log.d(TAG, "Connecting via Bluetooth")
        onEvent?.invoke("STATUS|Connecting to Phone A...")
        val ok = BluetoothClient.connect()
        // BluetoothClient.connect() returns true when it queued a background connect attempt;
        // only fire DISCONNECTED if it truly couldn't start (no adapter, BT off, etc.)
        if (!ok) {
            onEvent?.invoke("DISCONNECTED")
        }
    }

    fun send(message: String) = BluetoothClient.send(message)
    fun answer() = send("ANSWER")
    fun reject() = send("REJECT")
    fun hangup() = send("HANGUP")
    fun sendSms(number: String, body: String) = send("SMS_SEND|$number|$body")
    fun isConnected() = BluetoothClient.isConnected()
    fun activeTransportName() = "Bluetooth"
    fun isBluetoothActive() = true
    fun disconnect() { BluetoothClient.disconnect() }
}
