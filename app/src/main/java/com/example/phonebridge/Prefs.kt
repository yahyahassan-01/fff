package com.example.phonebridge

import android.content.Context

enum class Role { SERVER, CLIENT }

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("bridge", Context.MODE_PRIVATE)

    fun role(c: Context): Role? =
        sp(c).getString("role", null)?.let { Role.valueOf(it) }

    fun setRole(c: Context, r: Role) = sp(c).edit().putString("role", r.name).apply()

    /** Bluetooth MAC address of the paired Server phone (Client only). */
    fun serverAddress(c: Context): String = sp(c).getString("server_address", "") ?: ""
    fun setServerAddress(c: Context, a: String) = sp(c).edit().putString("server_address", a).apply()
}
