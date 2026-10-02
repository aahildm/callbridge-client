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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class IncomingCallActivity : AppCompatActivity() {

    private var callerNumber = ""
    private var knownName = ""
    private var callStartTime = 0L
    private var timerRunning = false
    private var isOutgoing = false
    private var onHold = false
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var tvCallStatus: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvCallLabel: TextView
    private lateinit var btnAnswer: ImageButton
    private lateinit var btnReject: ImageButton
    private lateinit var colAnswer: View
    private lateinit var tvRejectLabel: TextView
    private lateinit var rowInCallControls: View
    private lateinit var btnMute: View
    private lateinit var btnSpeaker: View
    private lateinit var btnHold: View
    private lateinit var ivMute: ImageView
    private lateinit var ivSpeaker: ImageView
    private lateinit var ivHold: ImageView
    private lateinit var tvHoldLabel: TextView

    private enum class Mode { INCOMING, OUTGOING, ACTIVE }
    private var mode = Mode.INCOMING

    private val AVATAR_COLORS = intArrayOf(
        0xFF5E97F6.toInt(), 0xFF33B679.toInt(), 0xFFE67C73.toInt(),
        0xFFF6BF26.toInt(), 0xFF8E24AA.toInt(), 0xFF039BE5.toInt())

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (timerRunning) {
                val elapsed = (System.currentTimeMillis() - callStartTime) / 1000
                val mins = elapsed / 60
                val secs = elapsed % 60
                tvTimer.text = String.format("%02d:%02d", mins, secs)
                if (!onHold && tvCallStatus.text == "Connected") tvCallStatus.visibility = View.GONE
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
        val callerName = intent.getStringExtra("caller_name") ?: ""
        knownName = callerName
        isOutgoing = intent.getBooleanExtra("outgoing", false)

        val tvCaller = findViewById<TextView>(R.id.tvCaller)
        tvCallStatus = findViewById(R.id.tvCallStatus)
        tvTimer = findViewById(R.id.tvTimer)
        tvCallLabel = findViewById(R.id.tvCallLabel)
        btnAnswer = findViewById(R.id.btnAnswer)
        btnReject = findViewById(R.id.btnReject)
        colAnswer = findViewById(R.id.colAnswer)
        tvRejectLabel = findViewById(R.id.tvRejectLabel)
        rowInCallControls = findViewById(R.id.rowInCallControls)
        btnMute = findViewById(R.id.btnMute)
        btnSpeaker = findViewById(R.id.btnSpeaker)
        btnHold = findViewById(R.id.btnHold)
        ivMute = findViewById(R.id.ivMute)
        ivSpeaker = findViewById(R.id.ivSpeaker)
        ivHold = findViewById(R.id.ivHold)
        tvHoldLabel = findViewById(R.id.tvHoldLabel)

        // Dark status/nav bars to match the call screen
        window.statusBarColor = Color.parseColor("#2B2D31")
        window.navigationBarColor = Color.parseColor("#131314")
        setAvatar(callerName, callerNumber)

        // Show name if known, number below; or just number if no contact found
        if (callerName.isNotBlank()) {
            tvCaller.text = callerName
            tvCallStatus.text = callerNumber
        } else {
            tvCaller.text = callerNumber
        }

        if (isOutgoing) {
            mode = Mode.OUTGOING
            tvCallLabel.text = "Calling"
            tvCallStatus.text = "Dialing via ${BluetoothClient.getSavedDeviceName() ?: "Phone A"}…"
            colAnswer.visibility = View.GONE
            tvRejectLabel.text = "Cancel"
        } else {
            mode = Mode.INCOMING
            tvCallLabel.text = "Incoming call"
            // If name is known, tvCallStatus already shows the number; don't overwrite
            if (callerName.isBlank()) tvCallStatus.text = "Mobile"
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
            when (mode) {
                Mode.ACTIVE -> {
                    TransportManager.hangup()
                    AudioClient.stop()
                    CallAudioController.reset()
                    stopTimer()
                    finish()
                }
                Mode.OUTGOING -> {
                    TransportManager.send("CANCEL_DIAL")
                    handler.postDelayed({ finish() }, 300)
                }
                Mode.INCOMING -> {
                    TransportManager.reject()
                    finish()
                }
            }
        }

        btnMute.setOnClickListener {
            setToggle(ivMute, CallAudioController.toggleMute())
        }

        btnSpeaker.setOnClickListener {
            setToggle(ivSpeaker, CallAudioController.toggleSpeaker())
        }

        btnHold.setOnClickListener {
            onHold = !onHold
            TransportManager.send(if (onHold) "HOLD" else "UNHOLD")
            setToggle(ivHold, onHold)
            tvHoldLabel.text = if (onHold) "Resume" else "Hold"
            tvCallStatus.text = if (onHold) "On hold" else if (knownName.isNotBlank()) callerNumber else "Connected"
            tvCallStatus.visibility = View.VISIBLE
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
            "DIALING" -> tvCallStatus.text = "Dialing…"
            "ACTIVE" -> {
                RingtoneHelper.stopRinging()
                mode = Mode.ACTIVE
                tvCallStatus.text = if (knownName.isNotBlank()) callerNumber else "Connected"
                tvCallLabel.text = "Ongoing call"
                colAnswer.visibility = View.GONE
                tvRejectLabel.text = "End call"
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

    /** Pixel-style toggle: light blue circle + dark icon when on, grey circle + white icon when off. */
    private fun setToggle(icon: ImageView, on: Boolean) {
        icon.isSelected = on
        icon.setColorFilter(if (on) Color.parseColor("#062E6F") else Color.WHITE)
    }

    /** Coloured circle with the contact's initial, or a person icon for unknown numbers. */
    private fun setAvatar(name: String, number: String) {
        val tvAvatar = findViewById<TextView>(R.id.tvAvatar)
        val ivAvatar = findViewById<ImageView>(R.id.ivAvatar)
        val avatar = findViewById<View>(R.id.avatar)
        val key = name.ifBlank { number }
        val color = AVATAR_COLORS[(key.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]
        (avatar.background.mutate() as? GradientDrawable)?.setColor(color)
        val initial = name.trim().firstOrNull { it.isLetter() }
        if (initial != null) {
            tvAvatar.text = initial.uppercaseChar().toString()
            tvAvatar.visibility = View.VISIBLE
            ivAvatar.visibility = View.GONE
        } else {
            tvAvatar.visibility = View.GONE
            ivAvatar.visibility = View.VISIBLE
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
            val name = intent.getStringExtra("caller_name") ?: ""
            knownName = name
            if (name.isNotBlank()) {
                findViewById<TextView>(R.id.tvCaller)?.text = name
                tvCallStatus.text = callerNumber
            } else {
                findViewById<TextView>(R.id.tvCaller)?.text = callerNumber
            }
            setAvatar(name, callerNumber)
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
