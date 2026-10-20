package com.example.phonebridge.server

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.phonebridge.Msg
import com.example.phonebridge.bt.Bt
import com.example.phonebridge.bt.BtLink
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/** Bluetooth RFCOMM server: the Client phone connects to it (the two phones must be paired first). */
@SuppressLint("MissingPermission")
class BtServer(private val ctx: Context) {

    @Volatile private var running = false
    @Volatile private var serverSocket: BluetoothServerSocket? = null
    private val links = CopyOnWriteArrayList<BtLink>()

    fun start() {
        running = true
        Thread({ acceptLoop() }, "bt-accept").start()
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        links.forEach { it.close() }
        links.clear()
    }

    private fun acceptLoop() {
        val adapter = ctx.getSystemService(BluetoothManager::class.java)?.adapter
        while (running) {
            try {
                if (adapter == null || !adapter.isEnabled) { Thread.sleep(2000); continue }
                val ss = adapter.listenUsingRfcommWithServiceRecord(Bt.SERVICE_NAME, Bt.UUID)
                serverSocket = ss
                val socket = ss.accept()          // blocks until a paired client connects
                runCatching { ss.close() }
                attach(socket)
            } catch (e: SecurityException) {
                Log.e(TAG, "Bluetooth permission missing", e); return
            } catch (e: IOException) {
                if (running) { Log.w(TAG, "accept failed: ${e.message}"); sleepQuietly(1000) }
            } catch (e: InterruptedException) {
                return
            }
        }
    }

    private fun attach(socket: BluetoothSocket) {
        Log.i(TAG, "client connected: ${socket.remoteDevice?.address}")
        links.forEach { it.close() }              // a (re)connecting client replaces a stale link
        links.clear()

        val link = BtLink(
            socket,
            onText = { runCatching { ServerHub.onCommand(JSONObject(it)) }.onFailure { e -> Log.w(TAG, "bad message", e) } },
            onAudio = { pcm -> ServerHub.audio?.onClientAudio(pcm) }
        )
        links += link
        link.closed.invokeOnCompletion { links.remove(link) }
        link.start()

        link.sendText(Msg.json(Msg.HELLO) { put("name", android.os.Build.MODEL) }.toString())
        // A client that connects mid-call still gets the Answer screen
        ServerHub.lastCallState?.let { link.sendText(it.toString()) }
    }

    fun broadcastText(text: String) = links.forEach { it.sendText(text) }

    fun broadcastAudio(pcm: ByteArray) = links.forEach { it.sendAudio(pcm, pcm.size) }

    private fun sleepQuietly(ms: Long) = try { Thread.sleep(ms) } catch (_: InterruptedException) {}

    private companion object { const val TAG = "BtServer" }
}
