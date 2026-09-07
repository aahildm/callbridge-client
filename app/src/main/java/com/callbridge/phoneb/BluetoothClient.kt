package com.callbridge.phoneb

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.util.UUID

object BluetoothClient {

    private val TAG = "CallBridge-BtClient"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val SECRET = "callbridge123"

    private var appContext: Context? = null
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var authenticated = false
    private var readThread: Thread? = null
    private var connectThread: Thread? = null

    var onEvent: ((String) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    @SuppressLint("MissingPermission")
    private fun bondedDevice(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        return adapter.bondedDevices?.firstOrNull()
    }

    /** Returns true if connection attempt was started (async). */
    @SuppressLint("MissingPermission")
    fun connect(): Boolean {
        disconnect()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth unavailable or disabled")
            return false
        }
        val device = bondedDevice() ?: run {
            Log.e(TAG, "No paired device found")
            return false
        }

        // Connect on a background thread to avoid ANR
        connectThread = Thread {
            try {
                adapter.cancelDiscovery()
                val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
                s.connect()
                socket = s
                out = s.outputStream
                authenticated = false
                Log.d(TAG, "BT connected to ${device.name} — authenticating")
                sendRaw("AUTH|$SECRET")
                startReadLoop(s)
            } catch (e: Exception) {
                Log.e(TAG, "Bluetooth connect failed: ${e.message}")
                onEvent?.invoke("DISCONNECTED")
            }
        }
        connectThread?.start()
        return true
    }

    private fun startReadLoop(s: BluetoothSocket) {
        readThread = Thread {
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream))
                while (true) {
                    val line = reader.readLine() ?: break
                    handleMessage(line)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Read loop ended: ${e.message}")
            } finally {
                authenticated = false
                onEvent?.invoke("DISCONNECTED")
            }
        }
        readThread?.start()
    }

    private fun handleMessage(message: String) {
        Log.d(TAG, "Received (BT): $message")
        if (message == "AUTH|OK") {
            authenticated = true
            Log.d(TAG, "Authenticated over Bluetooth")
            onEvent?.invoke("CONNECTED")
            return
        }
        if (message == "AUTH|FAIL") {
            Log.e(TAG, "Auth failed")
            disconnect()
            return
        }
        if (!authenticated) return
        onEvent?.invoke(message)
    }

    private fun sendRaw(message: String) {
        try {
            out?.write((message + "\n").toByteArray())
            out?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
        }
    }

    fun send(message: String) = sendRaw(message)
    fun answer() = send("ANSWER")
    fun reject() = send("REJECT")
    fun hangup() = send("HANGUP")
    fun sendSms(number: String, body: String) = send("SMS_SEND|$number|$body")

    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        out = null
        authenticated = false
    }

    fun isConnected() = socket?.isConnected == true && authenticated
}
