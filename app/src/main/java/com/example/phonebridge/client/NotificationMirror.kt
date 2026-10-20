package com.example.phonebridge.client

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

object NotificationMirror {
    private const val CHANNEL = "mirrored"

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, j: JSONObject) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Mirrored notifications").build()
        )
        val text = j.optString("text")
        val b = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)   // swap for your own vector asset
            .setContentTitle(j.optString("title"))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setSubText(j.optString("app"))
            .setWhen(j.optLong("time", System.currentTimeMillis()))
            .setShowWhen(true)
            .setAutoCancel(true)
        decodeIcon(j.optString("icon"))?.let { b.setLargeIcon(it) }

        // The server's key is used as the tag, so updates replace instead of piling up
        nm.notify(j.getString("key"), 0, b.build())
    }

    fun cancel(ctx: Context, key: String) =
        NotificationManagerCompat.from(ctx).cancel(key, 0)

    private fun decodeIcon(b64: String): Bitmap? {
        if (b64.isEmpty()) return null
        return runCatching {
            val bytes = Base64.decode(b64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }
}
