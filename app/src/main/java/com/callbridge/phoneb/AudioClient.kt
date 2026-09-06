package com.callbridge.phoneb

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object AudioClient {

    private val TAG = "CallBridge-Audio"
    private const val SAMPLE_RATE = 8000
    private const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
    private const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    private const val RECEIVE_PORT = 9001  // Receive from Phone A (call audio)
    private const val SEND_PORT = 9002     // Send to Phone A (mic audio)

    @Volatile private var running = false
    private var sendThread: Thread? = null
    private var receiveThread: Thread? = null

    fun start(phoneAIp: String) {
        if (running) return
        running = true
        Log.d(TAG, "Starting audio client, Phone A IP: $phoneAIp")

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)

        // Receive audio from Phone A → play on speaker
        receiveThread = Thread {
            try {
                val socket = DatagramSocket(RECEIVE_PORT)
                val outBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)
                val player = AudioTrack(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(ENCODING)
                        .setChannelMask(CHANNEL_OUT)
                        .build(),
                    outBufferSize,
                    AudioTrack.MODE_STREAM,
                    AudioManager.AUDIO_SESSION_ID_GENERATE
                )
                player.play()
                val buffer = ByteArray(outBufferSize)
                val packet = DatagramPacket(buffer, buffer.size)
                while (running) {
                    socket.receive(packet)
                    player.write(packet.data, 0, packet.length)
                }
                player.stop()
                player.release()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Receive error: ${e.message}")
            }
        }.also { it.start() }

        // Record mic → send to Phone A
        sendThread = Thread {
            try {
                val socket = DatagramSocket()
                val address = InetAddress.getByName(phoneAIp)
                val recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE, CHANNEL_IN, ENCODING, bufferSize
                )
                recorder.startRecording()
                val buffer = ByteArray(bufferSize)
                while (running) {
                    val read = recorder.read(buffer, 0, bufferSize)
                    if (read > 0) {
                        val packet = DatagramPacket(buffer, read, address, SEND_PORT)
                        socket.send(packet)
                    }
                }
                recorder.stop()
                recorder.release()
                socket.close()
            } catch (e: Exception) {
                Log.e(TAG, "Send error: ${e.message}")
            }
        }.also { it.start() }
    }

    fun stop() {
        running = false
        sendThread?.interrupt()
        receiveThread?.interrupt()
        sendThread = null
        receiveThread = null
        Log.d(TAG, "Audio stopped")
    }
}
