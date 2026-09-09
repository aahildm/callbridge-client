package com.callbridge.phoneb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

class ClientService : Service() {

    private val TAG = "CallBridge-ClientSvc"
    private val CHANNEL_ID = "callbridge_client"
    private val NOTIF_ID = 2

    private var phoneAIp = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification("Starting..."))

        val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        phoneAIp = prefs.getString("phone_a_ip", "") ?: ""

        TransportManager.init(this)
        TransportManager.onEvent = { event -> handleEvent(event) }

        if (phoneAIp.isNotEmpty()) {
            TransportManager.connect(phoneAIp)
        }
    }

    private fun handleEvent(event: String) {
        // Skip logging full audio/calllog payloads to avoid log spam
        if (!event.startsWith("CALLLOG|")) Log.d(TAG, "Event: $event")

        when {
            event.startsWith("STATUS|") -> {
                val msg = event.removePrefix("STATUS|")
                updateNotification(msg)
                broadcastStatus(msg)
            }
            event == "CONNECTED" -> {
                val transport = TransportManager.activeTransportName()
                updateNotification("✅ Connected via $transport")
                broadcastStatus("CONNECTED|$transport")
                // Ask for call log right after connecting in case auto-send missed it
                TransportManager.send("GET_CALLLOG")
            }
            event == "FALLBACK_BLUETOOTH" -> {
                updateNotification("🔄 WiFi failed — trying Bluetooth...")
                broadcastStatus("WiFi failed — trying Bluetooth...")
            }
            event == "DISCONNECTED" -> {
                updateNotification("🔴 Disconnected — retrying...")
                broadcastStatus("DISCONNECTED")
            }
            event.startsWith("RING|") -> {
                showIncomingCallScreen(event.removePrefix("RING|"))
                vibrate()
            }
            event == "STATE|DIALING" -> {
                broadcastCallState("DIALING")
            }
            event == "STATE|ACTIVE" -> {
                AudioClient.start(phoneAIp)
                broadcastCallState("ACTIVE")
            }
            event == "ENDED" -> {
                stopVibration()
                AudioClient.stop()
                broadcastCallState("ENDED")
                sendBroadcast(Intent("com.callbridge.phoneb.CALL_ENDED"))
            }
            event.startsWith("SMS_IN|") -> {
                val parts = event.removePrefix("SMS_IN|").split("|", limit = 3)
                if (parts.size == 3) {
                    val msg = SmsStore.Message(
                        sender = parts[0],
                        body = parts[2],
                        timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis(),
                        incoming = true
                    )
                    SmsStore.add(msg)
                    showSmsNotification(msg)
                }
            }
            event.startsWith("CALLLOG|") -> {
                CallLogStore.update(event.removePrefix("CALLLOG|"))
            }
            event.startsWith("DIAL_FAIL|") -> {
                broadcastStatus("Dial failed: ${event.removePrefix("DIAL_FAIL|")}")
            }
        }
    }

    private fun broadcastStatus(status: String) {
        sendBroadcast(Intent("com.callbridge.phoneb.STATUS").apply {
            putExtra("status", status)
        })
    }

    /** Notifies IncomingCallActivity (or any listener) about call state for timer/UI updates. */
    private fun broadcastCallState(state: String) {
        sendBroadcast(Intent("com.callbridge.phoneb.CALL_STATE").apply {
            putExtra("state", state)
        })
    }

    private fun showIncomingCallScreen(number: String) {
        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("caller_number", number)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            action = "INCOMING_CALL"
        }
        startActivity(intent)
    }

    private fun showSmsNotification(msg: SmsStore.Message) {
        val intent = Intent(this, SmsActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = buildNotifBuilder()
            .setContentTitle("SMS from ${msg.sender}")
            .setContentText(msg.body)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(msg.sender.hashCode(), notif)
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 500, 500, 500, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator
                ?.vibrate(android.os.VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            getSystemService(VibratorManager::class.java)?.defaultVibrator?.cancel()
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)?.cancel()
        }
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val newIp = intent?.getStringExtra("phone_a_ip")
        if (!newIp.isNullOrEmpty() && newIp != phoneAIp) {
            phoneAIp = newIp
            getSharedPreferences("callbridge", Context.MODE_PRIVATE)
                .edit().putString("phone_a_ip", newIp).apply()
            TransportManager.connect(newIp)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        TransportManager.disconnect()
        AudioClient.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "CallBridge", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "CallBridge connection status"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    private fun buildNotifBuilder(): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
        }
    }

    private fun buildNotification(text: String): Notification {
        return buildNotifBuilder()
            .setContentTitle("CallBridge")
            .setContentText(text)
            .build()
    }
}
