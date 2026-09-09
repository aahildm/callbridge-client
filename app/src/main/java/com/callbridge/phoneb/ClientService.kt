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
import android.util.Log

class ClientService : Service() {

    private val TAG = "CallBridge-ClientSvc"
    private val CHANNEL_ID = "callbridge_client"
    private val CALL_CHANNEL_ID = "callbridge_incoming_call"
    private val NOTIF_ID = 2
    private val CALL_NOTIF_ID = 3

    private var phoneAIp = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
                showIncomingCallNotification(event.removePrefix("RING|"))
            }
            event == "STATE|DIALING" -> broadcastCallState("DIALING")
            event == "STATE|ACTIVE" -> {
                AudioClient.start(phoneAIp)
                broadcastCallState("ACTIVE")
                getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIF_ID)
            }
            event == "STATE|HOLDING" -> broadcastCallState("HOLDING")
            event == "ENDED" -> {
                AudioClient.stop()
                broadcastCallState("ENDED")
                sendBroadcast(Intent("com.callbridge.phoneb.CALL_ENDED"))
                getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIF_ID)
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

    private fun broadcastCallState(state: String) {
        sendBroadcast(Intent("com.callbridge.phoneb.CALL_STATE").apply {
            putExtra("state", state)
        })
    }

    /**
     * Shows a full-screen call notification so the incoming call pops up
     * over the lock screen and other apps, the way a real phone call does,
     * instead of relying on startActivity from a background service (which
     * Android restricts on API 29+).
     */
    private fun showIncomingCallNotification(number: String) {
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("caller_number", number)
            action = "INCOMING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CALL_CHANNEL_ID)
                .setContentTitle("Incoming call")
                .setContentText(number)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_CALL)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Incoming call")
                .setContentText(number)
                .setSmallIcon(android.R.drawable.ic_menu_call)
                .setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setContentIntent(fullScreenPendingIntent)
                .setOngoing(true)
                .build()
        }

        getSystemService(NotificationManager::class.java)?.notify(CALL_NOTIF_ID, notif)
        // Also launch directly in case the app is already foregrounded —
        // full-screen intent alone sometimes doesn't fire if we're already visible.
        try {
            startActivity(fullScreenIntent)
        } catch (_: Exception) {}
    }

    private fun showSmsNotification(msg: SmsStore.Message) {
        val intent = Intent(this, SmsActivity::class.java)
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = buildNotifBuilder(CHANNEL_ID)
            .setContentTitle("SMS from ${msg.sender}")
            .setContentText(msg.body)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(msg.sender.hashCode(), notif)
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

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "CallBridge", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "CallBridge connection status"
                    setShowBadge(false)
                }
            )

            // Separate high-importance channel so incoming calls always break through
            nm?.createNotificationChannel(
                NotificationChannel(
                    CALL_CHANNEL_ID, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "CallBridge incoming call alerts"
                    setShowBadge(true)
                    setSound(null, null) // ringtone is played manually by RingtoneHelper
                }
            )
        }
    }

    private fun buildNotifBuilder(channelId: String = CHANNEL_ID): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, channelId)
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
