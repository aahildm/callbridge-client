package com.callbridge.phoneb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
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
    private val retryHandler = android.os.Handler(android.os.Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        startForeground(NOTIF_ID, buildNotification("Starting..."))
        TransportManager.init(this)
        AudioClient.init(this)
        TransportManager.onEvent = { event -> handleEvent(event) }
        TransportManager.connect()
    }

    private fun handleEvent(event: String) {
        if (!event.startsWith("CALLLOG|")) Log.d(TAG, "Event: $event")
        when {
            event.startsWith("STATUS|") -> {
                val msg = event.removePrefix("STATUS|")
                updateNotification(msg)
                // Audio diagnostics go to the notification only, not the connection pill
                if (!msg.startsWith("Uplink:") && !msg.startsWith("Downlink")) broadcastStatus(msg)
            }
            event == "CONNECTED" -> {
                updateNotification("✅ Connected to ${BluetoothClient.getSavedDeviceName() ?: "Phone A"}")
                broadcastStatus("CONNECTED|Bluetooth")
                TransportManager.send("GET_CALLLOG")
            }
            event == "DISCONNECTED" -> {
                updateNotification("🔴 Disconnected — retrying...")
                broadcastStatus("DISCONNECTED")
                retryHandler.removeCallbacksAndMessages(null)
                retryHandler.postDelayed({
                    if (!TransportManager.isConnected()) TransportManager.connect()
                }, 5000)
            }
            event.startsWith("RING|") -> {
                // Protocol: RING|number|name  (name may be empty for unknown callers)
                val payload = event.removePrefix("RING|")
                val pipeIdx = payload.indexOf('|')
                val number = if (pipeIdx >= 0) payload.substring(0, pipeIdx) else payload
                val name = if (pipeIdx >= 0) payload.substring(pipeIdx + 1) else ""
                CallStateStore.setCall(number, outgoing = false)
                CallStateStore.update("RINGING")
                showIncomingCallNotification(number, name)
            }
            event == "STATE|DIALING" -> CallStateStore.update("DIALING")
            event == "STATE|ACTIVE" -> {
                AudioClient.start()
                CallStateStore.update("ACTIVE")
                getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIF_ID)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    TransportManager.send("GET_UPLINK_STATUS")
                }, 1000)
            }
            event == "STATE|HOLDING" -> CallStateStore.update("HOLDING")
            event == "ENDED" -> {
                AudioClient.stop()
                CallStateStore.update("ENDED")
                sendBroadcast(Intent("com.callbridge.phoneb.CALL_ENDED"))
                getSystemService(NotificationManager::class.java)?.cancel(CALL_NOTIF_ID)
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({ CallStateStore.reset() }, 1000)
            }
            event.startsWith("SMS_IN|") -> {
                val parts = event.removePrefix("SMS_IN|").split("|", limit = 3)
                if (parts.size == 3) {
                    val msg = SmsStore.Message(sender = parts[0], body = parts[2],
                        timestamp = parts[1].toLongOrNull() ?: System.currentTimeMillis(), incoming = true)
                    SmsStore.add(msg)
                    val otp = extractOtp(msg.body)
                    if (otp != null) {
                        copyToClipboard(otp)
                        showOtpNotification(msg.sender, otp, msg.body)
                    } else {
                        showSmsNotification(msg)
                    }
                }
            }
            event.startsWith("CALLLOG|") -> CallLogStore.update(event.removePrefix("CALLLOG|"))
            event.startsWith("UPLINK_MODE|") ->
                getSharedPreferences("callbridge", Context.MODE_PRIVATE).edit()
                    .putString("uplink_mode", event.removePrefix("UPLINK_MODE|")).apply()
            event.startsWith("CONTACTS|") -> ContactsStore.update(event.removePrefix("CONTACTS|"))
            event.startsWith("DIAL_FAIL|") -> broadcastStatus("Dial failed: ${event.removePrefix("DIAL_FAIL|")}")
        }
    }

    private fun broadcastStatus(status: String) {
        sendBroadcast(Intent("com.callbridge.phoneb.STATUS").apply { putExtra("status", status) })
    }

    private fun showIncomingCallNotification(number: String, name: String = "") {
        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm?.canUseFullScreenIntent() == false) broadcastStatus("⚠️ Call popup blocked — enable in Setup tab")
        }
        val displayLabel = name.ifBlank { number }
        val fullScreenIntent = Intent(this, IncomingCallActivity::class.java).apply {
            putExtra("caller_number", number)
            putExtra("caller_name", name)
            action = "INCOMING_CALL"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val fsPendingIntent = PendingIntent.getActivity(this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CALL_CHANNEL_ID).setContentTitle("Incoming call")
                .setContentText(displayLabel)
                .setSmallIcon(android.R.drawable.ic_menu_call).setPriority(Notification.PRIORITY_MAX)
                .setCategory(Notification.CATEGORY_CALL).setFullScreenIntent(fsPendingIntent, true)
                .setContentIntent(fsPendingIntent).setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setContentTitle("Incoming call").setContentText(displayLabel)
                .setSmallIcon(android.R.drawable.ic_menu_call).setPriority(Notification.PRIORITY_MAX)
                .setFullScreenIntent(fsPendingIntent, true).setContentIntent(fsPendingIntent).setOngoing(true).build()
        }
        getSystemService(NotificationManager::class.java)?.notify(CALL_NOTIF_ID, notif)
        try { startActivity(fullScreenIntent) } catch (e: Exception) { Log.e(TAG, "Direct launch failed: ${e.message}") }
    }

    /** Extract a 4-8 digit OTP from an SMS body. Returns null if none found. */
    private fun extractOtp(body: String): String? {
        // Explicit keyword match first: "OTP", "code", "pin", "verify"
        val keywordPattern = Regex(
            """(?i)(?:otp|one.?time|verification\s+code|verify(?:ication)?\s+code|pin|passcode)[^\d]{0,15}(\d{4,8})""")
        keywordPattern.find(body)?.groupValues?.get(1)?.let { return it }

        // Generic: a standalone 4-8 digit block (not part of a longer number)
        val genericPattern = Regex("""(?<!\d)(\d{4,8})(?!\d)""")
        val matches = genericPattern.findAll(body).map { it.groupValues[1] }.toList()
        // Prefer 6-digit codes (most common OTP length), else first match
        return matches.firstOrNull { it.length == 6 } ?: matches.firstOrNull()
    }

    private fun copyToClipboard(text: String) {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText("OTP", text))
            Log.d(TAG, "OTP copied to clipboard: $text")
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard copy failed: ${e.message}")
        }
    }

    private fun showOtpNotification(sender: String, otp: String, fullBody: String) {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, SmsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("🔑 OTP: $otp  (copied!)")
                .setContentText("From $sender • $fullBody")
                .setStyle(Notification.BigTextStyle().bigText(fullBody))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pi).setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("🔑 OTP: $otp  (copied!)")
                .setContentText("From $sender • $fullBody")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pi).setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH).build()
        }
        getSystemService(NotificationManager::class.java)?.notify(sender.hashCode(), notif)
    }

    private fun showSmsNotification(msg: SmsStore.Message) {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, SmsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notif = buildNotifBuilder(CHANNEL_ID).setContentTitle("SMS from ${msg.sender}")
            .setContentText(msg.body).setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentIntent(pi).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java)?.notify(msg.sender.hashCode(), notif)
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)?.notify(NOTIF_ID, buildNotification(text))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_STICKY
    override fun onDestroy() { super.onDestroy(); TransportManager.disconnect(); AudioClient.stop() }
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "CallBridge", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "CallBridge connection status"; setShowBadge(false) })
            nm?.createNotificationChannel(NotificationChannel(CALL_CHANNEL_ID, "Incoming Calls", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "CallBridge incoming call alerts"; setShowBadge(true); setSound(null, null) })
        }
    }

    private fun buildNotifBuilder(channelId: String = CHANNEL_ID): Notification.Builder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, channelId).setSmallIcon(android.R.drawable.ic_menu_call).setOngoing(true)
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setSmallIcon(android.R.drawable.ic_menu_call).setOngoing(true)
        }
    }

    private fun buildMainIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildNotification(text: String) = buildNotifBuilder()
        .setContentTitle("CallBridge")
        .setContentText(text)
        .setContentIntent(buildMainIntent())
        .build()
}
