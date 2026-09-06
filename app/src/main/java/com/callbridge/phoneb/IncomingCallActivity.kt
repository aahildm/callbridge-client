package com.callbridge.phoneb

import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {

    private var callerNumber = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen and turn screen on
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        setContentView(R.layout.activity_incoming_call)

        callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"

        if (intent.action == "CALL_ENDED") {
            finish()
            return
        }

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
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == "CALL_ENDED") {
            AudioClient.stop()
            finish()
        } else if (intent?.action == "INCOMING_CALL") {
            callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"
            findViewById<TextView>(R.id.tvCaller)?.text = callerNumber
        }
    }
}
