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
    private var onHold = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvCallStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvCallLabel: TextView
    private lateinit var btnAnswer: Button
    private lateinit var btnReject: Button
    private lateinit var rowInCallControls: View
    private lateinit var btnMute: Button
    private lateinit var btnSpeaker: Button
    private lateinit var btnHold: Button

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

    // Handles any state change that happens WHILE this screen is open
    private val callStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyState(intent.getStringExtra("state") ?: return)
        }
    }

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            applyState("ENDED")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CallAudioController.init(this)

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
        btnHold = findViewById(R.id.btnHold)

        tvCaller.text = callerNumber

        if (isOutgoing) {
            tvCallLabel.text = "📞 CALLING"
            tvCallStatus.text = "Dialing via Phone A..."
            btnAnswer.visibility = View.GONE
            btnReject.text = "Cancel"
        } else {
            tvCallStatus.text = "Incoming call from Phone A"
            RingtoneHelper.startRinging(this)
        }

        btnAnswer.setOnClickListener {
            RingtoneHelper.stopRinging()
            TransportManager.answer()
            tvCallStatus.text = "Answering..."
            btnAnswer.isEnabled = false
        }

        btnReject.setOnClickListener {
            RingtoneHelper.stopRinging()
            when (btnReject.text) {
                "End Call" -> {
                    TransportManager.hangup()
                    AudioClient.stop()
                    CallAudioController.reset()
                    stopTimer()
                    finish()
                }
                "Cancel" -> {
                    TransportManager.send("CANCEL_DIAL")
                    handler.postDelayed({ finish() }, 300)
                }
                else -> {
                    TransportManager.reject()
                    finish()
                }
            }
        }

        btnMute.setOnClickListener {
            val nowMuted = CallAudioController.toggleMute()
            btnMute.text = if (nowMuted) "🎤 Unmute" else "🎤 Mute"
        }

        btnSpeaker.setOnClickListener {
            val nowOn = CallAudioController.toggleSpeaker()
            btnSpeaker.text = if (nowOn) "🔊 On" else "🔊 Speaker"
        }

        btnHold.setOnClickListener {
            onHold = !onHold
            TransportManager.send(if (onHold) "HOLD" else "UNHOLD")
            btnHold.text = if (onHold) "▶ Resume" else "⏸ Hold"
            tvCallStatus.text = if (onHold) "On hold" else "Connected"
        }

        val filter = IntentFilter("com.callbridge.phoneb.CALL_ENDED")
        val stateFilter = IntentFilter("com.callbridge.phoneb.CALL_STATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(callEndedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(callStateReceiver, stateFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(callEndedReceiver, filter)
            registerReceiver(callStateReceiver, stateFilter)
        }

        // KEY FIX: read whatever state already happened before this screen
        // finished registering its receiver — closes the race where
        // STATE|ACTIVE arrives between startActivity() and onCreate() completing.
        CallStateStore.onStateChanged = { state ->
            runOnUiThread { applyState(state) }
        }
        applyState(CallStateStore.currentState)
    }

    /** Single place that updates the UI for any given call state — used both
     *  for the initial state read and for every subsequent broadcast/callback. */
    private fun applyState(state: String) {
        when (state) {
            "DIALING" -> tvCallStatus.text = "Dialing..."
            "ACTIVE" -> {
                RingtoneHelper.stopRinging()
                tvCallStatus.text = "Connected"
                tvCallLabel.text = "📞 ON CALL"
                btnAnswer.visibility = View.GONE
                btnReject.text = "End Call"
                rowInCallControls.visibility = View.VISIBLE
                startTimer()
            }
            "HOLDING" -> tvCallStatus.text = "On hold"
            "ENDED" -> {
                RingtoneHelper.stopRinging()
                stopTimer()
                finish()
            }
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
            RingtoneHelper.startRinging(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RingtoneHelper.stopRinging()
        stopTimer()
        CallAudioController.reset()
        CallStateStore.onStateChanged = null
        try { unregisterReceiver(callEndedReceiver) } catch (_: Exception) {}
        try { unregisterReceiver(callStateReceiver) } catch (_: Exception) {}
    }
}
