package com.callbridge.phoneb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {

    private var callerNumber = ""
    private var callStartTime = 0L
    private var timerRunning = false
    private var isOutgoing = false
    private var muted = false
    private var speakerOn = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvCallStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvCallLabel: TextView
    private lateinit var btnAnswer: Button
    private lateinit var btnReject: Button
    private lateinit var rowInCallControls: View
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsed = (System.currentTimeMillis() - callStartTime) / 1000
                val mins = elapsed / 60
                val secs = elapsed % 60
                tvTimer.text = String.format("%02d:%02d", mins, secs)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra("state")) {
                "DIALING" -> tvCallStatus.text = "Dialing..."
                "ACTIVE" -> {
                    tvCallStatus.text = "Connected"
                    tvCallLabel.text = "📞 ON CALL"
                    btnAnswer.visibility = View.GONE
                    btnReject.text = "End Call"
                    rowInCallControls.visibility = View.VISIBLE
                    startTimer()
                }
                "ENDED" -> {
                    stopTimer()
                    finish()
                }
            }
        }
    }

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AudioClient.stop()
            stopTimer()
            finish()
        }
    }

    // Reflects mute/speaker state confirmed back by Phone A
    private val audioStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                "com.callbridge.phoneb.MUTE_STATE" -> {
                    muted = intent.getBooleanExtra("muted", false)
                    btnMute.text = if (muted) "🎤 Unmute" else "🎤 Mute"
                }
                "com.callbridge.phoneb.SPEAKER_STATE" -> {
                    speakerOn = intent.getBooleanExtra("on", false)
                    btnSpeaker.text = if (speakerOn) "🔊 Speaker On" else "🔊 Speaker"
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        isOutgoing = intent.getBooleanExtra("outgoing", false)

        val tvCaller = findViewById<TextView>(R.id.tvCaller)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvCallLabel = findViewById(R.id.tvCallLabel)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnReject = findViewById(R.id.btnReject)
        rowInCallControls = findViewById(R.id.rowInCallControls)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)

        tvCaller.text = callerNumber

        if (isOutgoing) {
            tvCallLabel.text = "📞 CALLING"
            tvCallStatus.text = "Dialing via Phone A..."
            btnAnswer.visibility = View.GONE
            btnReject.text = "Cancel"
        } else {
            tvCallStatus.text = "Incoming call from Phone A"
        }

        btnAnswer.setOnClickListener {
            TransportManager.answer()
            tvCallStatus.text = "Answering..."
            btnAnswer.isEnabled = false
        }

        btnReject.setOnClickListener {
            when (btnReject.text) {
                "End Call" -> {
                    TransportManager.hangup()
                    AudioClient.stop()
                    stopTimer()
                    finish()
                }
                "Cancel" -> {
                    TransportManager.send("CANCEL_DIAL")
                    finish()
                }
                else -> {
                    TransportManager.reject()
                    finish()
                }
            }
        }

        btnMute.setOnClickListener {
            TransportManager.send(if (muted) "UNMUTE" else "MUTE")
        }

        btnSpeaker.setOnClickListener {
            TransportManager.send(if (speakerOn) "SPEAKER_OFF" else "SPEAKER_ON")
        }

        val filter = IntentFilter("com.callbridge.phoneb.CALL_ENDED")
        val stateFilter = IntentFilter("com.callbridge.phoneb.CALL_STATE")
        val muteFilter = IntentFilter("com.callbridge.phoneb.MUTE_STATE")
        val speakerFilter = IntentFilter("com.callbridge.phoneb.SPEAKER_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(callStateReceiver, stateFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(audioStateReceiver, muteFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(audioStateReceiver, speakerFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, filter)
            registerReceiver(callStateReceiver, stateFilter)
            registerReceiver(audioStateReceiver, muteFilter)
            registerReceiver(audioStateReceiver, speakerFilter)
        }
    }

    private fun startTimer() {
        if (timerRunning) return
        callStartTime = System.currentTimeMillis()
        timerRunning = true
        tvTimer.visibility = View.VISIBLE
        handler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        handler.removeCallbacks(timerRunnable)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == "INCOMING_CALL") {
            callerNumber = intent.getStringExtra("caller_number") ?: "Unknown"
            findViewById<TextView>(R.id.tvCaller)?.text = callerNumber
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
        try { unregisterReceiver(callEndedReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(callStateReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(audioStateReceiver) } catch (_: Exception) {}
    }
}
