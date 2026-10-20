package com.example.phonebridge.audio

import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

class PcmPlayer {
    private val queue = ArrayBlockingQueue<ByteArray>(Audio.MAX_QUEUED_FRAMES)
    @Volatile private var running = false

    fun start() {
        val minBuf = AudioTrack.getMinBufferSize(
            Audio.RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(Audio.voiceAttrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(Audio.RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(minBuf, Audio.FRAME_BYTES * 4))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        running = true
        Thread({ loop(track) }, "pcm-player").start()
    }

    /** Called from the WebSocket thread; never blocks. When full, drop the oldest frame to bound latency. */
    fun push(pcm: ByteArray) {
        if (!running) return
        if (!queue.offer(pcm)) { queue.poll(); queue.offer(pcm) }
    }

    fun stop() { running = false }   // the thread releases the track itself

    private fun loop(track: AudioTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        try {
            track.play()
            while (running) {
                val frame = queue.poll(100, TimeUnit.MILLISECONDS) ?: continue
                track.write(frame, 0, frame.size)      // blocking write paces playback
            }
        } finally {
            runCatching { track.stop() }
            track.release()
            queue.clear()
        }
    }
}
