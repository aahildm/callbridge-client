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

    var onEvent: ((String) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun connect(ip: String) {
        intentionalDisconnect = false
        serverIp = ip
        disconnect()
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
                if (!intentionalDisconnect && serverIp.isNotEmpty()) {
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
            if (!intentionalDisconnect) scheduleReconnect()
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

    fun disconnect() {
        intentionalDisconnect = true
        try { client?.close() } catch (_: Exception) {}
        client = null
    }

    private fun scheduleReconnect() {
        onEvent?.invoke("STATUS|Retrying in 5s...")
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!intentionalDisconnect && serverIp.isNotEmpty()) {
                Log.d(TAG, "Reconnecting to $serverIp")
                connect(serverIp)
            }
        }, 5000)
    }

    fun isConnected() = client?.isOpen == true && authenticated
}
