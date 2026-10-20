package com.example.phonebridge.client

import android.annotation.SuppressLint
import android.app.*
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.phonebridge.CallInfo
import com.example.phonebridge.Msg
import com.example.phonebridge.Prefs
import com.example.phonebridge.bt.Bt
import com.example.phonebridge.bt.BtLink
import kotlinx.coroutines.*
import org.json.JSONObject

class ClientService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audioEngine by lazy { ClientAudioEngine(applicationContext) }

    private var loopJob: Job? = null
    private var status = "Connecting…"
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ClientHub.audio = audioEngine
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonebridge:client")
            .apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        when (intent?.action) {   // notification-button actions
            ACTION_ANSWER -> ClientHub.send(Msg.CALL_ANSWER)
            ACTION_DECLINE -> ClientHub.send(Msg.CALL_DECLINE)
            ACTION_HANGUP -> ClientHub.send(Msg.CALL_HANGUP)
            else -> startConnectLoop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ClientHub.socket?.close()
        ClientHub.socket = null
        resetCall()
        ClientHub.audio = null
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------- connection ----------
    /** Connect to the paired Server over Bluetooth; reconnect forever with backoff. */
    private fun startConnectLoop() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch(Dispatchers.IO) {
            var attempt = 0
            while (isActive) {
                val link = tryConnect()
                if (link != null) {
                    attempt = 0
                    ClientHub.socket = link
                    setStatus("Connected over Bluetooth")
                    link.closed.await()                 // suspends until the link drops
                    lost(link)
                    delay(1000)
                } else {
                    setStatus("Server not reachable, retrying…")
                    delay(minOf(1000L shl minOf(attempt++, 4), 10_000L))   // 1,2,4,8,10,10…s
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun tryConnect(): BtLink? {
        val address = Prefs.serverAddress(this)
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter
        if (address.isEmpty() || adapter == null || !adapter.isEnabled) return null
        return try {
            val socket = adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(Bt.UUID)
            try {
                socket.connect()                        // blocking; fails fast if the Server isn't listening
            } catch (e: Exception) {
                runCatching { socket.close() }
                throw e
            }
            BtLink(
                socket,
                onText = { runCatching { handle(JSONObject(it)) }.onFailure { e -> Log.w(TAG, "bad message", e) } },
                onAudio = { pcm -> ClientHub.audio?.onServerAudio(pcm) }
            ).also { it.start() }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission missing", e); null
        } catch (e: Exception) {
            Log.w(TAG, "connect failed: ${e.message}"); null
        }
    }

    private fun lost(link: BtLink) {
        if (ClientHub.socket === link) ClientHub.socket = null
        resetCall()                                  // connection gone => drop any call UI
        setStatus("Disconnected, retrying…")
    }

    // ---------- message dispatch ----------
    private fun handle(j: JSONObject) {
        when (j.optString("type")) {
            Msg.HELLO -> setStatus("Connected to ${j.optString("name", "server")}")
            Msg.NOTIFICATION -> NotificationMirror.show(this, j)
            Msg.NOTIFICATION_REMOVED -> NotificationMirror.cancel(this, j.optString("key"))
            Msg.CALL_STATE -> handleCall(j)
        }
    }

    private fun handleCall(j: JSONObject) {
        val state = j.optString("state")
        if (state == "ended") { resetCall(); return }
        val prev = ClientHub.callInfo.value?.state
        val info = CallInfo(state, j.optString("number"), j.optString("name"))
        ClientHub.callInfo.value = info
        ClientCallNotifier.show(this, info)
        if (state == "active" && prev != "active") ClientHub.audio?.onCallActive()
    }

    private fun resetCall() {
        if (ClientHub.callInfo.value == null) return
        ClientHub.callInfo.value = null
        ClientCallNotifier.cancel(this)
        ClientHub.audio?.onCallEnded()
    }

    // ---------- foreground notification ----------
    private fun foregroundNotification(): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(FG_CHANNEL, "Bridge client", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, FG_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("PhoneBridge client")
            .setContentText(status)
            .setOngoing(true)
            .build()
    }

    private fun startInForeground() {
        val type = if (Build.VERSION.SDK_INT >= 30)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0
        ServiceCompat.startForeground(this, FG_ID, foregroundNotification(), type)
    }

    private fun setStatus(text: String) {
        status = text
        getSystemService(NotificationManager::class.java).notify(FG_ID, foregroundNotification())
    }

    companion object {
        const val ACTION_ANSWER = "com.example.phonebridge.ANSWER"
        const val ACTION_DECLINE = "com.example.phonebridge.DECLINE"
        const val ACTION_HANGUP = "com.example.phonebridge.HANGUP"
        private const val FG_ID = 1
        private const val FG_CHANNEL = "client"
        private const val TAG = "ClientService"
    }
}
