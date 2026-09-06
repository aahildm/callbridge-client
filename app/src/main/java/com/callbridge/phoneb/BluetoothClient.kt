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

/**
 * Bluetooth RFCOMM fallback transport for Phone B. Mirrors SocketClient's
 * public interface (connect/send/answer/reject/hangup/sendSms/isConnected/
 * onEvent) so ClientService/TransportManager can treat both the same way.
 */
object BluetoothClient {

    private val TAG = "CallBridge-BtClient"
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private const val SECRET = "callbridge123" // Must match Phone A

    private var appContext: Context? = null
    private var socket: BluetoothSocket? = null
    private var out: OutputStream? = null
    private var authenticated = false
    private var readThread: Thread? = null

    var onEvent: ((String) -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /** Name of a paired device to prefer, if known (e.g. saved from a prior successful connect). */
    fun pairedDeviceName(): String? = bondedDevice()?.name

    @SuppressLint("MissingPermission")
    private fun bondedDevice(): BluetoothDevice? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        // Assumes the phones have been paired once via Android Bluetooth settings,
        // and that Phone A is the only (or first) bonded device.
        return adapter.bondedDevices?.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    fun connect(): Boolean {
        disconnect()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            Log.e(TAG, "Bluetooth unavailable or disabled")
            return false
        }
        val device = bondedDevice() ?: run {
            Log.e(TAG, "No paired device found — pair with Phone A first")
            return false
        }

        return try {
            adapter.cancelDiscovery()
            val s = device.createRfcommSocketToServiceRecord(SPP_UUID)
            s.connect()
            socket = s
            out = s.outputStream
            authenticated = false
            Log.d(TAG, "Connected to ${device.name} via Bluetooth — authenticating")
            send("AUTH|$SECRET")
            startReadLoop(s)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Bluetooth connect failed: ${e.message}")
            false
        }
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
            Log.e(TAG, "Auth failed — wrong secret")
            disconnect()
            return
        }
        if (!authenticated) return
        onEvent?.invoke(message)
    }

    fun send(message: String) {
        try {
            out?.write((message + "\n").toByteArray())
            out?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Send failed: ${e.message}")
        }
    }

    fun answer() = send("ANSWER")
    fun reject() = send("REJECT")
    fun hangup() = send("HANGUP")
    fun sendSms(number: String, body: String) = send("SMS_SEND|$number|$body")

    fun disconnect() {
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        out = null
        authenticated = false
    }

    fun isConnected() = socket?.isConnected == true && authenticated
}
