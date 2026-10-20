package com.example.phonebridge.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.AudioEffect
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import android.util.Log

class PcmRecorder(
    private val source: Int,
    private val useEffects: Boolean,          // AEC + noise suppression (Client only)
    private val onFrame: (ByteArray) -> Unit
) {
    @Volatile private var running = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        val rec = try {
            val minBuf = AudioRecord.getMinBufferSize(
                Audio.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            AudioRecord.Builder()
                .setAudioSource(source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(Audio.RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, Audio.FRAME_BYTES * 4))
                .build()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord build failed", e)
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        running = true
        Thread({ loop(rec) }, "pcm-recorder").start()
        return true
    }

    fun stop() { running = false }   // the thread releases the recorder itself

    private fun loop(rec: AudioRecord) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val effects = if (useEffects) attachEffects(rec.audioSessionId) else emptyList()
        try {
            rec.startRecording()
            while (running) {
                val buf = ByteArray(Audio.FRAME_BYTES)
                var off = 0
                while (running && off < buf.size) {
                    val n = rec.read(buf, off, buf.size - off)
                    if (n < 0) {
                        Log.e(TAG, "read error $n")
                        running = false
                        break
                    }
                    off += n
                }
                if (running) onFrame(buf)
            }
        } finally {
            runCatching { rec.stop() }
            rec.release()
            effects.forEach { it.release() }
        }
    }

    private fun attachEffects(session: Int): List<AudioEffect> {
        val list = mutableListOf<AudioEffect>()
        if (AcousticEchoCanceler.isAvailable()) {
            AcousticEchoCanceler.create(session)?.let { it.setEnabled(true); list.add(it) }
        }
        if (NoiseSuppressor.isAvailable()) {
            NoiseSuppressor.create(session)?.let { it.setEnabled(true); list.add(it) }
        }
        return list
    }

    private companion object { const val TAG = "PcmRecorder" }
}
