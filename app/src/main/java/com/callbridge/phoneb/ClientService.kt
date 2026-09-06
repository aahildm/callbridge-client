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
    private val CALL_NOTIF_ID = 3

    private var phoneAIp = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIF_ID, buildStatusNotification("Connecting..."))

        val prefs = getSharedPreferences("callbridge", Context.MODE_PRIVATE)
        phoneAIp = prefs.getString("phone_a_ip", "") ?: ""

        TransportManager.init(this)
        TransportManager.onEvent = { event -> handleEvent(event) }

        if (phoneAIp.isNotEmpty()) {
            TransportManager.connect(phoneAIp)
        }
    }

    private fun handleEvent(event: String) {
        Log.d(TAG, "Event: $event")
        when {
            event == "CONNECTED" -> {
                updateNotification("✅ Connected via ${TransportManager.activeTransportName()}")
            }
            event == "FALLBACK_BLUETOOTH" -> {
                updateNotification("🔄 WiFi unavailable — trying Bluetooth...")
            }
            event == "DISCONNECTED" -> {
                updateNotification("🔴 Disconnected — retrying...")
            }
            event.startsWith("RING|") -> {
                val number = event.removePrefix("RING|")
                showIncomingCallScreen(number)
                vibrate()
            }
            event == "ENDED" -> {
                dismissCallNotification()
                AudioClient.stop()
                // Close IncomingCallActivity if open
                val intent = Intent(this, IncomingCallActivity::class.java)
                intent.action = "CALL_ENDED"
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            }
            event.startsWith("STATE|ACTIVE") -> {
                dismissCallNotification()
                AudioClient.start(phoneAIp)
            }
            event.startsWith("SMS_IN|") -> {
                // SMS_IN|sender|timestamp|body
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
        }
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
        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("SMS from ${msg.sender}")
                .setContentText(msg.body)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("SMS from ${msg.sender}")
                .setContentText(msg.body)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
        }

        getSystemService(NotificationManager::class.java)
            ?.notify(msg.sender.hashCode(), notif)
    }

    private fun dismissCallNotification() {
        getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIF_ID)
    }

    private fun vibrate() {
        val pattern = longArrayOf(0, 500, 500, 500, 500, 500)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator?.vibrate(
                android.os.VibrationEffect.createWaveform(pattern, 0)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = getSystemService(Vibrator::class.java)
            v?.vibrate(pattern, 0)
        }
    }

    private fun updateNotification(text: String) {
        val notif = buildStatusNotification(text)
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, notif)
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

    private fun buildStatusNotification(text: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("CallBridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("CallBridge")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setOngoing(true)
                .build()
        }
    }
}
