package com.example.phonebridge.server

import android.annotation.SuppressLint
import android.content.Context
import android.telecom.TelecomManager
import android.util.Log
import com.example.phonebridge.CallInfo
import com.example.phonebridge.Msg
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject

/** Implemented by the audio bridge (ServerAudioBridge). */
interface AudioListener {
    fun onCallActive()
    fun onCallEnded()
    fun onClientAudio(pcm: ByteArray)
}

object ServerHub {
    private const val TAG = "ServerHub"

    lateinit var appContext: Context
    @Volatile var server: BtServer? = null
    @Volatile var audio: AudioListener? = null
    @Volatile var lastCallState: JSONObject? = null   // replayed to clients that connect mid-call

    val callInfo = MutableStateFlow<CallInfo?>(null)   // observed by InCallActivity
    val micLevel = MutableStateFlow(0)                 // live pickup level, shown on the call screen
    val micStatus = MutableStateFlow("")              // which mic source is in use / blocked

    fun send(json: JSONObject) {
        server?.broadcastText(json.toString())
    }

    fun sendAudio(pcm: ByteArray) {
        server?.broadcastAudio(pcm)
    }

    /** info == null means the call ended. */
    fun publishCall(info: CallInfo?) {
        if (info == null && callInfo.value == null) return   // ignore duplicate "ended"
        callInfo.value = info
        val j = Msg.json(Msg.CALL_STATE) {
            put("state", info?.state ?: "ended")
            put("number", info?.number ?: "")
            put("name", info?.name ?: "")
        }
        lastCallState = if (info == null) null else j
        send(j)
    }

    @SuppressLint("MissingPermission")
    fun onCommand(msg: JSONObject) {
        Log.d(TAG, "command: ${msg.optString("type")}")
        val incall = BridgeInCallService.instance
        when (msg.optString("type")) {
            Msg.CALL_ANSWER -> {
                if (incall != null) incall.answerRinging()
                else telecom().acceptRingingCall()   // fallback, needs ANSWER_PHONE_CALLS
            }
            Msg.CALL_DECLINE -> incall?.rejectRinging()
            Msg.CALL_HANGUP -> incall?.hangUp()
        }
    }

    private fun telecom() = appContext.getSystemService(TelecomManager::class.java)
}
