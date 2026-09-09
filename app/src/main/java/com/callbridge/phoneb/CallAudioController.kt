package com.callbridge.phoneb

import android.content.Context
import android.media.AudioManager

/** Controls Phone B's own mic mute and speakerphone for the call audio stream. */
object CallAudioController {

    private var audioManager: AudioManager? = null
    private var muted = false
    private var speakerOn = false

    fun init(context: Context) {
        audioManager = context.getSystemService(AudioManager::class.java)
    }

    fun isMuted() = muted
    fun isSpeakerOn() = speakerOn

    fun setMute(mute: Boolean) {
        muted = mute
        // AudioClient checks this flag before sending mic data upstream
        AudioClient.setMuted(mute)
    }

    fun toggleMute(): Boolean {
        setMute(!muted)
        return muted
    }

    fun setSpeaker(on: Boolean) {
        speakerOn = on
        try {
            audioManager?.let { am ->
                am.isSpeakerphoneOn = on
                am.mode = AudioManager.MODE_IN_COMMUNICATION
            }
        } catch (_: Exception) {}
    }

    fun toggleSpeaker(): Boolean {
        setSpeaker(!speakerOn)
        return speakerOn
    }

    fun reset() {
        muted = false
        speakerOn = false
        AudioClient.setMuted(false)
        try { audioManager?.isSpeakerphoneOn = false } catch (_: Exception) {}
    }
}
