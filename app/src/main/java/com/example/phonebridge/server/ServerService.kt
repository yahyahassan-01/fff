package com.example.phonebridge.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class ServerService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val audioBridge = ServerAudioBridge()

    override fun onCreate() {
        super.onCreate()
        ServerHub.appContext = applicationContext
        ServerHub.audio = audioBridge
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "phonebridge:server")
            .apply { acquire() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground()
        if (ServerHub.server == null) {
            runCatching {
                ServerHub.server = BtServer(applicationContext).also { it.start() }
            }.onFailure { Log.e("ServerService", "start failed", it) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { ServerHub.server?.stop() }
        ServerHub.server = null
        audioBridge.onCallEnded()
        ServerHub.audio = null
        wakeLock?.takeIf { it.isHeld }?.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startInForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bridge server", NotificationManager.IMPORTANCE_LOW)
        )
        val n: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("PhoneBridge server running")
            .setContentText("Waiting for the client phone over Bluetooth")
            .setOngoing(true)
            .build()

        val type = if (Build.VERSION.SDK_INT >= 30)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0
        ServiceCompat.startForeground(this, 1, n, type)
    }

    private companion object { const val CHANNEL = "server" }
}
