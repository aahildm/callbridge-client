package com.callbridge.phoneb

import android.content.Context
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

object SocketClient {

    private val TAG = "CallBridge-Client"
    private const val SECRET = "callbridge123"
    private const val PORT = 8765

    private var client: WebSocketClient? = null
    private var serverIp: String = ""
    private var appContext: Context? = null
    private var authenticated = false
    @Volatile private var intentionalDisconnect = false
    // Hard kill-switch: when true, WiFi is not allowed to (re)connect at all,
    // regardless of any pending reconnect timers already queued. Set by
    // TransportManager when Bluetooth is forced, cleared when WiFi is chosen.
    @Volatile private var suspended = false

    var onEvent: ((String) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Blocks all connect attempts (including already-scheduled reconnects)
     *  until resume() is called. Used when switching to Bluetooth so a
     *  stale WiFi reconnect timer can't silently take back over. */
    fun suspend() {
        suspended = true
        disconnect()
    }

    fun resume() {
        suspended = false
    }

    fun connect(ip: String) {
        if (suspended) {
            Log.d(TAG, "connect() ignored — WiFi is suspended")
            return
        }
        intentionalDisconnect = false
        serverIp = ip
        disconnectSocketOnly()
        authenticated = false

        val uri = URI("ws://$ip:$PORT")
        Log.d(TAG, "Connecting to $uri")
        onEvent?.invoke("STATUS|Connecting to $ip:$PORT...")

        client = object : WebSocketClient(uri) {

            override fun onOpen(handshake: ServerHandshake) {
                Log.d(TAG, "TCP connected — sending auth")
                onEvent?.invoke("STATUS|Connected — authenticating...")
                send("AUTH|$SECRET")
            }

            override fun onMessage(message: String) {
                Log.d(TAG, "Received: $message")
                when (message) {
                    "AUTH|OK" -> {
                        authenticated = true
                        Log.d(TAG, "Auth OK")
                        onEvent?.invoke("CONNECTED")
                    }
                    "AUTH|FAIL" -> {
                        Log.e(TAG, "Auth failed — wrong secret")
                        onEvent?.invoke("STATUS|Auth failed — wrong secret key")
                        close()
                    }
                    else -> {
                        if (authenticated) onEvent?.invoke(message)
                    }
                }
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                Log.d(TAG, "Disconnected: code=$code reason=$reason remote=$remote")
                authenticated = false
                val detail = if (reason.isNotEmpty()) reason else "code $code"
                onEvent?.invoke("STATUS|Disconnected ($detail)")
                onEvent?.invoke("DISCONNECTED")
                if (!intentionalDisconnect && !suspended && serverIp.isNotEmpty()) {
                    scheduleReconnect()
                }
            }

            override fun onError(ex: Exception) {
                Log.e(TAG, "WebSocket error: ${ex.javaClass.simpleName}: ${ex.message}")
                onEvent?.invoke("STATUS|Error: ${ex.javaClass.simpleName}: ${ex.message}")
            }
        }

        try {
            client?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connect exception: ${e.message}")
            onEvent?.invoke("STATUS|Failed to connect: ${e.message}")
            if (!intentionalDisconnect && !suspended) scheduleReconnect()
        }
    }

    fun send(message: String) {
        try {
            if (client?.isOpen == true) client?.send(message)
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
        }
    }

    fun answer() = send("ANSWER")
    fun reject() = send("REJECT")
    fun hangup() = send("HANGUP")
    fun sendSms(number: String, body: String) = send("SMS_SEND|$number|$body")

    /** Full disconnect: stops the socket and blocks any pending reconnect. */
    fun disconnect() {
        intentionalDisconnect = true
        disconnectSocketOnly()
    }

    private fun disconnectSocketOnly() {
        try { client?.close() } catch (_: Exception) {}
        client = null
    }

    private fun scheduleReconnect() {
        onEvent?.invoke("STATUS|Retrying in 5s...")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            // Re-check both flags at fire time, not just at schedule time —
            // this is what closes the race that let a stale timer reconnect
            // WiFi after the user had already switched to Bluetooth.
            if (!intentionalDisconnect && !suspended && serverIp.isNotEmpty()) {
                Log.d(TAG, "Reconnecting to $serverIp")
                connect(serverIp)
            } else {
                Log.d(TAG, "Reconnect skipped — suspended=$suspended intentional=$intentionalDisconnect")
            }
        }, 5000)
    }

    fun isConnected() = client?.isOpen == true && authenticated
}
