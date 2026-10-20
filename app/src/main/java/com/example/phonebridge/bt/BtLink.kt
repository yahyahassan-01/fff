package com.example.phonebridge.bt

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * One connected RFCOMM socket with simple framing: [type:1][length:4][payload].
 * Replaces the WebSocket: text frames carry JSON, audio frames carry raw PCM.
 */
class BtLink(
    private val socket: BluetoothSocket,
    private val onText: (String) -> Unit,
    private val onAudio: (ByteArray) -> Unit
) {
    /** Completes when the link is closed for any reason. */
    val closed = CompletableDeferred<Unit>()

    private val input = DataInputStream(socket.inputStream)
    private val output = DataOutputStream(BufferedOutputStream(socket.outputStream, 4096))
    private val writeLock = Any()

    fun start() {
        Thread({ readLoop() }, "bt-read").start()
    }

    fun sendText(text: String): Boolean {
        val b = text.toByteArray(Charsets.UTF_8)
        return write(Bt.FRAME_TEXT, b, b.size)
    }

    fun sendAudio(pcm: ByteArray, len: Int): Boolean = write(Bt.FRAME_AUDIO, pcm, len)

    private fun write(type: Int, data: ByteArray, len: Int): Boolean {
        if (closed.isCompleted) return false
        return try {
            synchronized(writeLock) {
                output.writeByte(type)
                output.writeInt(len)
                output.write(data, 0, len)
                output.flush()
            }
            true
        } catch (e: IOException) {
            Log.w(TAG, "write failed", e); close(); false
        }
    }

    private fun readLoop() {
        try {
            while (true) {
                val type = input.readUnsignedByte()
                val len = input.readInt()
                if (len < 0 || len > Bt.MAX_FRAME) throw IOException("bad frame length $len")
                val buf = ByteArray(len)
                input.readFully(buf)
                when (type) {
                    Bt.FRAME_TEXT -> onText(String(buf, Charsets.UTF_8))
                    Bt.FRAME_AUDIO -> onAudio(buf)
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "link closed: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "read loop error", e)
        } finally {
            close()
        }
    }

    fun close() {
        closed.complete(Unit)
        runCatching { socket.close() }
    }

    private companion object { const val TAG = "BtLink" }
}
