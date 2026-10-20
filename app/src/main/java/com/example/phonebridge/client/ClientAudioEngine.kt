package com.example.phonebridge.client

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.example.phonebridge.audio.Audio
import com.example.phonebridge.audio.PcmPlayer
import com.example.phonebridge.audio.PcmRecorder

class ClientAudioEngine(ctx: Context) : ClientAudioListener {

    private val am = ctx.getSystemService(AudioManager::class.java)
    private val lock = Any()
    private var player: PcmPlayer? = null
    private var recorder: PcmRecorder? = null
    private var focus: AudioFocusRequest? = null
    @Volatile private var hangoverUntil = 0L

    override fun onCallActive() {
        synchronized(lock) {
            if (player != null) return                       // idempotent
            am.mode = AudioManager.MODE_IN_COMMUNICATION     // enables the platform voice-call audio path
            setSpeaker(false)                                // earpiece by default
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(Audio.voiceAttrs).build()
                .also { am.requestAudioFocus(it) }           // pauses music

            player = PcmPlayer().also { it.start() }
            recorder = PcmRecorder(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, useEffects = true, onFrame = ::onMicFrame
            ).takeIf { it.start() }
            if (recorder == null) Log.e(TAG, "mic capture failed")
        }
    }

    override fun onCallEnded() {
        synchronized(lock) {
            recorder?.stop(); recorder = null
            player?.stop(); player = null
            focus?.let { am.abandonAudioFocusRequest(it) }; focus = null
            setSpeaker(false)
            am.mode = AudioManager.MODE_NORMAL
        }
    }

    override fun onServerAudio(pcm: ByteArray) {
        if (pcm.size == Audio.FRAME_BYTES) player?.push(pcm)
    }

    /** Voice gate: send only while you speak (+300 ms hangover). This also drives the Server's ducking. */
    private fun onMicFrame(frame: ByteArray) {
        val now = SystemClock.elapsedRealtime()
        if (Audio.rms(frame) > VAD_RMS) hangoverUntil = now + HANGOVER_MS
        if (now < hangoverUntil) ClientHub.sendAudio(frame, frame.size)
    }

    /** Not wired to any button yet. Call it from your UI to toggle loudspeaker. */
    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (on) am.availableCommunicationDevices
                .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { am.setCommunicationDevice(it) }
            else am.clearCommunicationDevice()
        } else {
            am.isSpeakerphoneOn = on
        }
    }

    private companion object {
        const val TAG = "ClientAudioEngine"
        const val VAD_RMS = 200        // tune: raise it in noisy rooms
        const val HANGOVER_MS = 300L
    }
}
