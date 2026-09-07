package com.callbridge.phoneb

import android.content.Context
import android.util.Log
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

object SocketClient {

    private val TAG = "CallBridge-Client"
    private const val SECRET = "callbridge123"

    private var client: WebSocketClient? = null
    private var serverIp: String = ""
    private const val PORT = 8765
    private var appContext: Context? = null
    private var authenticated = false
    // Flag to suppress reconnect after intentional disconnect
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
        client = object : WebSocketClient(uri) {

            override fun onOpen(handshake: ServerHandshake) {
                Log.d(TAG, "Connected to Phone A — authenticating")
                send("AUTH|$SECRET")
            }

            override fun onMessage(message: String) {
                Log.d(TAG, "Received: $message")

                if (message == "AUTH|OK") {
                    authenticated = true
                    Log.d(TAG, "Authenticated successfully")
                    onEvent?.invoke("CONNECTED")
                    return
                }
                if (message == "AUTH|FAIL") {
                    Log.e(TAG, "Auth failed — wrong secret")
                    close()
                    return
                }

                if (!authenticated) return
                onEvent?.invoke(message)
            }

            override fun onClose(code: Int, reason: String, remote: Boolean) {
                Log.d(TAG, "Disconnected ($reason)")
                authenticated = false
                onEvent?.invoke("DISCONNECTED")
                if (!intentionalDisconnect && serverIp.isNotEmpty()) {
                    scheduleReconnect()
                }
            }

            override fun onError(ex: Exception) {
                Log.e(TAG, "WebSocket error: ${ex.message}")
            }
        }

        try {
            client?.connect()
        } catch (e: Exception) {
            Log.e(TAG, "Connect failed: ${e.message}")
            if (!intentionalDisconnect) scheduleReconnect()
        }
    }

    fun send(message: String) {
        try {
            if (client?.isOpen == true) {
                client?.send(message)
            }
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
        try {
            client?.close()
            client = null
        } catch (e: Exception) {
            Log.e(TAG, "Disconnect error: ${e.message}")
        }
    }

    private fun scheduleReconnect() {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (!intentionalDisconnect && serverIp.isNotEmpty()) {
                Log.d(TAG, "Reconnecting to $serverIp")
                connect(serverIp)
            }
        }, 5000)
    }

    fun isConnected() = client?.isOpen == true && authenticated
}
