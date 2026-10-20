package com.example.phonebridge.server

import android.util.Log
import com.example.phonebridge.Msg
import org.java_websocket.WebSocket
import org.java_websocket.handshake.ClientHandshake
import org.java_websocket.server.WebSocketServer
import org.json.JSONObject
import java.net.InetSocketAddress
import java.nio.ByteBuffer

class BridgeServer(port: Int) : WebSocketServer(InetSocketAddress(port)) {

    init {
        isReuseAddr = true
        connectionLostTimeout = 10   // ping/pong keepalive, drops dead clients
    }

    override fun onStart() {
        Log.i(TAG, "WebSocket server listening on $port")
    }

    override fun onOpen(conn: WebSocket, handshake: ClientHandshake) {
        Log.i(TAG, "client connected: ${conn.remoteSocketAddress}")
        conn.send(Msg.json(Msg.HELLO) { put("name", android.os.Build.MODEL) }.toString())
        // A client that connects (or reconnects) mid-call still gets the Answer screen
        ServerHub.lastCallState?.let { conn.send(it.toString()) }
    }

    override fun onClose(conn: WebSocket, code: Int, reason: String?, remote: Boolean) {
        Log.i(TAG, "client disconnected ($code)")
    }

    override fun onMessage(conn: WebSocket, message: String) {
        runCatching { ServerHub.onCommand(JSONObject(message)) }
            .onFailure { Log.w(TAG, "bad message", it) }
    }

    // Binary frames = raw PCM from the client mic
    override fun onMessage(conn: WebSocket, message: ByteBuffer) {
        val pcm = ByteArray(message.remaining()).also { message.get(it) }
        ServerHub.audio?.onClientAudio(pcm)
    }

    override fun onError(conn: WebSocket?, ex: Exception) {
        Log.e(TAG, "ws error", ex)
    }

    private companion object { const val TAG = "BridgeServer" }
}
