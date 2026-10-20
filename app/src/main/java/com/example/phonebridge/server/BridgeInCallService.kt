package com.example.phonebridge.server

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import com.example.phonebridge.CallInfo

class BridgeInCallService : InCallService() {

    companion object {
        @Volatile var instance: BridgeInCallService? = null
    }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish(call, state)
    }

    override fun onCreate() { super.onCreate(); instance = this }
    override fun onDestroy() { instance = null; super.onDestroy() }

    override fun onCallAdded(call: Call) {
        call.registerCallback(callback)
        publish(call, call.state)
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        val next = calls.firstOrNull { it.state != Call.STATE_DISCONNECTED }
        if (next != null) { publish(next, next.state); return }   // e.g. call-waiting
        ServerHub.publishCall(null)
        CallNotifier.cancel(this)
        ServerHub.audio?.onCallEnded()
    }

    private fun publish(call: Call, state: Int) {
        val label = when (state) {
            Call.STATE_RINGING -> "ringing"
            Call.STATE_ACTIVE -> "active"
            Call.STATE_DIALING, Call.STATE_CONNECTING -> "dialing"
            Call.STATE_HOLDING -> "holding"
            else -> return            // DISCONNECTED is cleaned up in onCallRemoved
        }
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val name = ContactLookup.name(this, number) ?: call.details.callerDisplayName.orEmpty()
        val info = CallInfo(label, number, name)

        ServerHub.publishCall(info)
        CallNotifier.show(this, info)

        if (label == "active") {
            // Telecom audio can't be captured by 3rd-party apps: use speaker + mic
            @Suppress("DEPRECATION")
            setAudioRoute(CallAudioState.ROUTE_SPEAKER)
            ServerHub.audio?.onCallActive()   // idempotent
        }
    }

    // ---- Commands (from the Client or the local UI) ----
    fun answerRinging() {
        calls.firstOrNull { it.state == Call.STATE_RINGING }?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectRinging() {
        calls.firstOrNull { it.state == Call.STATE_RINGING }?.reject(false, null)
    }

    fun hangUp() {
        calls.firstOrNull { it.state != Call.STATE_DISCONNECTED }?.disconnect()
    }
}
