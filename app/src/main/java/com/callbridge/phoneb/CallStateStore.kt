package com.callbridge.phoneb

/**
 * Single source of truth for the current call state, owned by ClientService.
 * UI reads this directly on creation instead of relying only on a broadcast
 * that could fire before the UI has registered its receiver — the same
 * "read current state, then subscribe to updates" pattern used by real
 * dialer/VoIP apps (Eyecon, WhatsApp) to avoid missed-event races.
 */
object CallStateStore {
    // NONE, DIALING, RINGING, ACTIVE, HOLDING, ENDED
    @Volatile var currentState: String = "NONE"
    @Volatile var currentCallerNumber: String = ""
    @Volatile var isOutgoingCall: Boolean = false

    var onStateChanged: ((String) -> Unit)? = null

    fun update(state: String) {
        currentState = state
        onStateChanged?.invoke(state)
    }

    fun setCall(number: String, outgoing: Boolean) {
        currentCallerNumber = number
        isOutgoingCall = outgoing
    }

    fun reset() {
        currentState = "NONE"
        currentCallerNumber = ""
        isOutgoingCall = false
    }
}
