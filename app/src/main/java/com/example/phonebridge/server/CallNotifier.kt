package com.example.phonebridge.server

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.phonebridge.CallInfo

object CallNotifier {
    private const val CHANNEL = "calls"
    private const val ID = 42

    fun show(ctx: Context, info: CallInfo) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Calls", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val ringing = info.state == "ringing"
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(info.title)
            .setContentText(if (ringing) "Incoming call" else info.state.replaceFirstChar { it.uppercase() })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pi)
            .apply { if (ringing) setFullScreenIntent(pi, true) }
            .build()
        nm.notify(ID, n)
    }

    fun cancel(ctx: Context) =
        ctx.getSystemService(NotificationManager::class.java).cancel(ID)
}
