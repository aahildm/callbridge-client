package com.callbridge.phoneb

import android.content.Context
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
    // 20 ms frame @ 16 kHz / mono / 16-bit = 640 bytes; use 2× min for stability
    private const val FRAME_BYTES = 640

    @Volatile private var running = false
    @Volatile private var muted = false
    private var sendThread: Thread? = null
    private var btPlayer: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null

    private var audioManager: AudioManager? = null
    private var prevMode = AudioManager.MODE_NORMAL

    fun init(context: Context) {
        audioManager = context.applicationContext
            .getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun setMuted(mute: Boolean) { muted = mute }

    fun onBluetoothAudio(base64Chunk: String) {
        if (!running) return
        try {
            val pcm = Base64.decode(base64Chunk, Base64.NO_WRAP)
            btPlayer?.write(pcm, 0, pcm.size)
        } catch (e: Exception) { Log.e(TAG, "BT audio decode error: ${e.message}") }
    }

    fun start(phoneAIp: String = "") {
        // Ensure a clean slate — stop any leftover state from a previous call
        if (running) {
            Log.w(TAG, "start() called while already running — stopping first")
            stopInternal()
        }
        running = true
        Log.d(TAG, "Starting audio over Bluetooth")

        // Route audio to earpiece with echo cancellation
        audioManager?.let {
            prevMode = it.mode
            it.mode = AudioManager.MODE_IN_COMMUNICATION
            it.isSpeakerphoneOn = false
        }

        // Playback track: receives the remote caller's voice (from server)
        val minOut = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
        val outBuf = maxOf(minOut * 2, FRAME_BYTES * 4)
        btPlayer = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setEncoding(ENCODING)
                .setChannelMask(CHANNEL_OUT)
                .build(),
            outBuf, AudioTrack.MODE_STREAM,
            audioManager?.generateAudioSessionId() ?: AudioManager.AUDIO_SESSION_ID_GENERATE)
        btPlayer?.play()

        // Capture thread: mic on Poco → send to server (server plays to remote caller)
        val minIn = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
        val inBuf = maxOf(minIn * 2, FRAME_BYTES * 4)
        sendThread = Thread {
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, inBuf)

                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed to init")
                    return@Thread
                }
                attachAudioEffects(recorder.audioSessionId)
                recorder.startRecording()
                Log.d(TAG, "Mic capture started")

                val buffer = ByteArray(FRAME_BYTES)
                while (running) {
                    val read = recorder.read(buffer, 0, FRAME_BYTES)
                    if (read > 0 && !muted) {
                        val chunk = Base64.encodeToString(buffer, 0, read, Base64.NO_WRAP)
                        BluetoothClient.send("AUDIO|$chunk")
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Mic capture error: ${e.message}")
            } finally {
                try { recorder?.stop() } catch (_: Exception) {}
                recorder?.release()
                releaseAudioEffects()
            }
        }.also { it.isDaemon = true; it.start() }
    }

    private fun attachAudioEffects(sessionId: Int) {
        try {
            if (AcousticEchoCanceler.isAvailable())
                echoCanceler = AcousticEchoCanceler.create(sessionId)?.apply { enabled = true }
            if (NoiseSuppressor.isAvailable())
                noiseSuppressor = NoiseSuppressor.create(sessionId)?.apply { enabled = true }
            if (AutomaticGainControl.isAvailable())
                agc = AutomaticGainControl.create(sessionId)?.apply { enabled = true }
        } catch (e: Exception) { Log.e(TAG, "Audio effects error: ${e.message}") }
    }

    private fun releaseAudioEffects() {
        try { echoCanceler?.release() } catch (_: Exception) {}
        try { noiseSuppressor?.release() } catch (_: Exception) {}
        try { agc?.release() } catch (_: Exception) {}
        echoCanceler = null; noiseSuppressor = null; agc = null
    }

    private fun stopInternal() {
        running = false
        muted = false
        audioManager?.mode = prevMode
        prevMode = AudioManager.MODE_NORMAL
        sendThread?.interrupt()
        sendThread = null
        try { btPlayer?.stop() } catch (_: Exception) {}
        btPlayer?.release()
        btPlayer = null
        releaseAudioEffects()
    }

    fun stop() {
        if (!running) return
        Log.d(TAG, "Audio stopped")
        stopInternal()
    }
}
