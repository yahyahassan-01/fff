package com.example.phonebridge.server

import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.example.phonebridge.audio.Audio
import com.example.phonebridge.audio.PcmPlayer
import com.example.phonebridge.audio.PcmRecorder

/**
 * Bridges call audio between the phone's speaker/mic and the Client.
 *
 * The caller's voice can only be captured acoustically (speakerphone -> mic). Some Android builds
 * silence certain mic sources during a call, so if a source delivers digital silence we
 * automatically move on to the next one.
 */
class ServerAudioBridge : AudioListener {

    private val lock = Any()
    private val main = Handler(Looper.getMainLooper())
    private var player: PcmPlayer? = null
    private var recorder: PcmRecorder? = null
    private var sourceIdx = 0
    private var zeroFrames = 0
    @Volatile private var lastClientVoiceAt = 0L

    override fun onCallActive() {
        synchronized(lock) {
            if (player != null) return                   // already running (state can repeat)
            player = PcmPlayer().also { it.start() }     // Client voice -> Server speaker -> call
            sourceIdx = 0
            startRecorder()
        }
    }

    override fun onCallEnded() {
        synchronized(lock) {
            recorder?.stop(); recorder = null
            player?.stop(); player = null
        }
        ServerHub.micLevel.value = 0
        ServerHub.micStatus.value = ""
    }

    override fun onClientAudio(pcm: ByteArray) {
        if (pcm.size != Audio.FRAME_BYTES) return
        if (Audio.rms(pcm) > CLIENT_VOICE_RMS) lastClientVoiceAt = SystemClock.elapsedRealtime()
        player?.push(pcm)
    }

    /** Must be called with [lock] held. Starts the first mic source (from sourceIdx) that opens. */
    private fun startRecorder() {
        while (sourceIdx < SOURCES.size) {
            val (name, src) = SOURCES[sourceIdx]
            val idx = sourceIdx
            zeroFrames = 0
            // useEffects=false: an echo canceller would remove the caller's voice (it is just speaker output)
            val r = PcmRecorder(src, useEffects = false) { frame -> onMicFrame(frame, idx) }
            if (r.start()) {
                recorder = r
                ServerHub.micStatus.value = "Mic source: $name"
                Log.i(TAG, "mic source $name started")
                return
            }
            Log.w(TAG, "mic source $name failed to open")
            sourceIdx++
        }
        ServerHub.micStatus.value = "Mic unavailable"
    }

    private fun onMicFrame(frame: ByteArray, idx: Int) {
        ServerHub.micLevel.value = Audio.rms(frame)

        // OS-silenced capture gives exact zeros (a quiet room still has a little noise).
        if (frame.all { it.toInt() == 0 }) {
            if (++zeroFrames == SILENT_FRAMES_BEFORE_SWITCH) main.post { switchSource(idx) }
        } else {
            zeroFrames = 0
        }

        val clientTalking = SystemClock.elapsedRealtime() - lastClientVoiceAt < DUCK_MS
        if (!clientTalking) ServerHub.sendAudio(frame)     // duck while the Client speaks
    }

    private fun switchSource(silentIdx: Int) {
        synchronized(lock) {
            if (player == null || sourceIdx != silentIdx) return    // call ended or already switched
            Log.w(TAG, "mic source ${SOURCES[silentIdx].first} is silent, trying next")
            recorder?.stop(); recorder = null
            sourceIdx++
            if (sourceIdx >= SOURCES.size) {
                ServerHub.micStatus.value = "Mic blocked by Android during calls"
                return
            }
            startRecorder()
        }
    }

    private companion object {
        const val TAG = "ServerAudioBridge"
        const val DUCK_MS = 200L          // keep muting the return path this long after the Client speaks
        const val CLIENT_VOICE_RMS = 400  // tune: higher = less sensitive
        const val SILENT_FRAMES_BEFORE_SWITCH = 75    // 75 x 20 ms = 1.5 s of pure digital silence

        val SOURCES = listOf(
            "MIC" to MediaRecorder.AudioSource.MIC,
            "VOICE_RECOGNITION" to MediaRecorder.AudioSource.VOICE_RECOGNITION,
            "UNPROCESSED" to MediaRecorder.AudioSource.UNPROCESSED,
            "CAMCORDER" to MediaRecorder.AudioSource.CAMCORDER
        )
    }
}
