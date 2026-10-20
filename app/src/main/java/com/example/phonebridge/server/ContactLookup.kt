package com.example.phonebridge.server

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import java.util.concurrent.ConcurrentHashMap

object ContactLookup {
    private val hits = ConcurrentHashMap<String, String>()   // only successful matches are cached

    fun name(ctx: Context, number: String): String? {
        if (number.isBlank()) return null
        hits[number]?.let { return it }
        if (ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED)
            return null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
        )
        val found = runCatching {
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

        if (found != null) hits[number] = found
        return found
    }
}
