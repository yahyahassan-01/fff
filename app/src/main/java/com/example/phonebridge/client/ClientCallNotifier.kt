package com.example.phonebridge.client

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import com.example.phonebridge.CallInfo

object ClientCallNotifier {
    private const val CHANNEL = "incoming_calls"
    private const val ID = 2001

    @SuppressLint("MissingPermission")
    fun show(ctx: Context, info: CallInfo) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL, NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Calls").build()
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val screen = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, IncomingCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )
        fun action(a: String, code: Int) = PendingIntent.getService(
            ctx, code, Intent(ctx, ClientService::class.java).setAction(a), flags
        )

        val person = Person.Builder().setName(info.title).setImportant(true).build()
        val style = if (info.state == "ringing")
            NotificationCompat.CallStyle.forIncomingCall(
                person,
                action(ClientService.ACTION_DECLINE, 1),
                action(ClientService.ACTION_ANSWER, 2)
            )
        else
            NotificationCompat.CallStyle.forOngoingCall(
                person, action(ClientService.ACTION_HANGUP, 3)
            )

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setStyle(style)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)   // shows over the lock screen (CallStyle also requires it)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(ID, n)
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}
