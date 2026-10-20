package com.example.phonebridge.audio

import android.media.AudioAttributes
import kotlin.math.sqrt

object Audio {
    const val RATE = 16_000
    const val FRAME_MS = 20
    const val FRAME_BYTES = RATE / 1000 * FRAME_MS * 2     // 640 bytes
    const val MAX_QUEUED_FRAMES = 10                       // 200 ms jitter cap

    val voiceAttrs: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Root-mean-square level of little-endian PCM16. Roughly 0..32767. */
    fun rms(pcm: ByteArray): Int {
        val n = pcm.size / 2
        if (n == 0) return 0
        var sum = 0.0
        for (i in 0 until n) {
            val s = ((pcm[2 * i + 1].toInt() shl 8) or (pcm[2 * i].toInt() and 0xFF)).toShort().toInt()
            sum += s.toDouble() * s
        }
        return sqrt(sum / n).toInt()
    }
}
