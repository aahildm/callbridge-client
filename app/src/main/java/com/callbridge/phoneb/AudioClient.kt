package com.callbridge.phoneb

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AutomaticGainControl
import android.media.audiofx.NoiseSuppressor
import android.util.Base64
import android.util.Log

object AudioClient {
    private val TAG = "CallBridge-Audio"
    private const val SAMPLE_RATE = 16000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    @Volatile private var running = false
    @Volatile private var muted = false
    private var sendThread: Thread? = null
    private var btPlayer: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    fun setMuted(mute: Boolean) { muted = mute }

    fun onBluetoothAudio(base64Chunk: String) {
        if (!running) return
        try {
            val pcm = Base64.decode(base64Chunk, Base64.NO_WRAP)
            btPlayer?.write(pcm, 0, pcm.size)
        } catch (e: Exception) { Log.e(TAG, "BT audio decode error: ${e.message}") }
    }

    fun start(phoneAIp: String = "") {
        if (running) return
        running = true
        Log.d(TAG, "Starting audio over Bluetooth")
        val outBuf = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        btPlayer = AudioTrack(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build(),
            AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(ENCODING)
                .setChannelMask(CHANNEL_OUT).build(),
            outBuf, AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE)
        btPlayer?.play()

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        sendThread = Thread {
            try {
                val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize)
                attachAudioEffects(recorder.audioSessionId)
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                while (running) {
                    val read = recorder.read(buffer, 0, bufferSize)
                    if (read > 0 && !muted) {
                        val chunk = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                        BluetoothClient.send("AUDIO|$chunk")
                    }
                }
                recorder.stop(); recorder.release()
            } catch (e: Exception) { Log.e(TAG, "BT send error: ${e.message}") }
            finally { releaseAudioEffects() }
        }.also { it.start() }
    }

    private fun attachAudioEffects(sessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable()) echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            if (AutomaticGainControl.isAvailable()) agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
        } catch (e: Exception) { Log.e(TAG, "Audio effects error: ${e.message}") }
    }

    private fun releaseAudioEffects() {
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { agc?.release() } catch (_: Exception) {}
        echoCanceler = null; noiseSuppressor = null; agc = null
    }

    fun stop() {
        running = false; muted = false
        btPlayer?.stop(); btPlayer?.release(); btPlayer = null
        sendThread?.interrupt(); sendThread = null
        releaseAudioEffects()
        Log.d(TAG, "Audio stopped")
    }
}
