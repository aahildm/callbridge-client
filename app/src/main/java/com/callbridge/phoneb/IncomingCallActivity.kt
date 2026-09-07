package com.callbridge.phoneb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {

    private var callerNumber = ""

    // Receives CALL_ENDED broadcast from ClientService
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AudioClient.stop()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContentView(R.layout.activity_incoming_call)

        callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"

        val tvCaller = findViewById<TextView>(R.id.tvCaller)
        val tvStatus = findViewById<TextView>(R.id.tvCallStatus)
        val btnAnswer = findViewById<Button>(R.id.btnAnswer)
        val btnReject = findViewById<Button>(R.id.btnReject)

        tvCaller.text = callerNumber
        tvStatus.text = "Incoming call from Phone A"

        btnAnswer.setOnClickListener {
            TransportManager.answer()
            tvStatus.text = "Connected..."
            btnAnswer.isEnabled = false
            btnReject.text = "End Call"
            btnReject.setOnClickListener {
                TransportManager.hangup()
                AudioClient.stop()
                finish()
            }
        }

        btnReject.setOnClickListener {
            TransportManager.reject()
            finish()
        }

        // Register for CALL_ENDED broadcast
        val filter = IntentFilter("com.callbridge.phoneb.CALL_ENDED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, filter)
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == "INCOMING_CALL") {
            callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"
            findViewById<TextView>(R.id.tvCaller)?.text = callerNumber
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(callEndedReceiver) } catch (_: Exception) {}
    }
}
