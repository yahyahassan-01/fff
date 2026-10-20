package com.example.phonebridge

import org.json.JSONObject

object Msg {
    // Server -> Client
    const val HELLO = "hello"
    const val NOTIFICATION = "notification"
    const val NOTIFICATION_REMOVED = "notification_removed"
    const val CALL_STATE = "call_state"      // state: ringing|active|dialing|holding|ended

    // Client -> Server
    const val CALL_ANSWER = "call_answer"
    const val CALL_DECLINE = "call_decline"
    const val CALL_HANGUP = "call_hangup"

    fun json(type: String, block: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject().put("type", type).apply(block)
}

data class CallInfo(val state: String, val number: String, val name: String) {
    val title: String get() = name.ifBlank { number.ifBlank { "Unknown caller" } }
}
