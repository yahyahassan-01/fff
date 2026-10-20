package com.example.phonebridge.client

import com.example.phonebridge.CallInfo
import com.example.phonebridge.Msg
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.phonebridge.bt.BtLink

/** Implemented by the audio engine in Step 5. */
interface ClientAudioListener {
    fun onCallActive()
    fun onCallEnded()
    fun onServerAudio(pcm: ByteArray)
}

object ClientHub {
    @Volatile var socket: BtLink? = null
    @Volatile var audio: ClientAudioListener? = null
    val callInfo = MutableStateFlow<CallInfo?>(null)   // observed by IncomingCallActivity

    fun send(type: String) { socket?.sendText(Msg.json(type).toString()) }

    fun sendAudio(pcm: ByteArray, len: Int) { socket?.sendAudio(pcm, len) }
}
