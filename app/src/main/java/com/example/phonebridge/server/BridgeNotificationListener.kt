package com.example.phonebridge.server

import android.app.Notification
import android.graphics.Bitmap
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import com.example.phonebridge.Msg
import java.io.ByteArrayOutputStream

class BridgeNotificationListener : NotificationListenerService() {

    private class Lru<V>(private val max: Int) : LinkedHashMap<String, V>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>?): Boolean = size > max
    }

    private val lastByKey = Lru<Int>(200)      // notification key -> content fingerprint
    private val recent = Lru<Long>(200)        // content -> last sent time

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification
        if (sbn.packageName == packageName) return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (n.category == Notification.CATEGORY_CALL) return

        val extras = n.extras
        if (extras.getInt(Notification.EXTRA_PROGRESS_MAX, 0) > 0) return   // download/progress spam

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        val content = "${sbn.packageName}\u0000$title\u0000$text"
        val fp = content.hashCode()
        val now = System.currentTimeMillis()
        synchronized(this) {
            if (lastByKey[sbn.key] == fp) return                       // layer 1: same key, same content
            lastByKey[sbn.key] = fp
            val last = recent[content]
            if (last != null && now - last < DUP_WINDOW_MS) return     // layer 2: same content, any key
            recent[content] = now
        }

        ServerHub.send(Msg.json(Msg.NOTIFICATION) {
            put("key", sbn.key)
            put("pkg", sbn.packageName)
            put("app", appLabel(sbn.packageName))
            put("title", title)
            put("text", text)
            put("time", sbn.postTime)
            put("icon", iconBase64(sbn) ?: "")
        })
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        synchronized(this) { lastByKey.remove(sbn.key) }   // a re-post after dismissal is a new event
        ServerHub.send(Msg.json(Msg.NOTIFICATION_REMOVED) { put("key", sbn.key) })
    }

    private fun appLabel(pkg: String): String = runCatching {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    /** Large icon (e.g. a contact photo) if present, otherwise the app launcher icon. */
    private fun iconBase64(sbn: StatusBarNotification): String? = runCatching {
        val d = sbn.notification.getLargeIcon()?.loadDrawable(this)
            ?: packageManager.getApplicationIcon(sbn.packageName)
        val out = ByteArrayOutputStream()
        d.toBitmap(96, 96).compress(Bitmap.CompressFormat.PNG, 100, out)
        Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }.getOrNull()

    private companion object { const val DUP_WINDOW_MS = 2_000L }
}
