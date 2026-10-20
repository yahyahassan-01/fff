package com.example.phonebridge

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.role.RoleManager
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.phonebridge.client.ClientService
import com.example.phonebridge.server.DialerActivity
import com.example.phonebridge.server.ServerService

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Screen() } }
    }

    @Composable
    private fun Screen() {
        val ctx = this
        var role by remember { mutableStateOf(Prefs.role(ctx) ?: Role.SERVER) }
        var selected by remember { mutableStateOf(Prefs.serverAddress(ctx)) }
        var devices by remember { mutableStateOf(bondedDevices()) }

        val btLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { devices = bondedDevices() }

        LaunchedEffect(role) {
            if (role == Role.CLIENT && !hasBluetoothPermission()) {
                btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                devices = bondedDevices()
            }
        }

        val dialerLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { proceed(role) }

        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            if (result.values.all { it }) {
                if (role == Role.SERVER) requestServerRoles { dialerLauncher.launch(it) }
                else proceed(role)
            } else {
                Toast.makeText(ctx, "All permissions are required", Toast.LENGTH_LONG).show()
            }
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("PhoneBridge", style = MaterialTheme.typography.headlineMedium)

            Role.values().forEach { r ->
                Row(
                    Modifier.fillMaxWidth()
                        .selectable(selected = role == r, onClick = { role = r }),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = role == r, onClick = { role = r })
                    Text(
                        if (r == Role.SERVER) "Server (main phone with SIM)"
                        else "Client (secondary phone)"
                    )
                }
            }

            // Pair the two phones once in the system Bluetooth settings, then pick the Server here.
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
            ) { Text("Pair phones (Bluetooth settings)") }

            if (role == Role.CLIENT) {
                Text("Server phone (paired devices)", style = MaterialTheme.typography.titleMedium)
                if (devices.isEmpty()) {
                    Text("No paired devices. Pair with the Server phone first, then come back.")
                    OutlinedButton(onClick = {
                        if (hasBluetoothPermission()) devices = bondedDevices()
                        else btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    }) { Text("Refresh") }
                }
                devices.forEach { (name, address) ->
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = selected == address, onClick = { selected = address }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected == address, onClick = { selected = address })
                        Text("$name  ($address)")
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (role == Role.CLIENT && selected.isEmpty()) {
                        Toast.makeText(ctx, "Pick the Server phone first", Toast.LENGTH_LONG).show()
                        return@Button
                    }
                    Prefs.setRole(ctx, role)
                    if (role == Role.CLIENT) Prefs.setServerAddress(ctx, selected)
                    permLauncher.launch(requiredRuntimePermissions(role))
                }
            ) { Text("Start") }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { stopAll() }
            ) { Text("Stop") }

            if (role == Role.SERVER) {
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { startActivity(Intent(ctx, DialerActivity::class.java)) }
                ) { Text("Open dialer") }
            }

            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { requestBatteryExemption() }
            ) { Text("Allow background running") }
        }
    }

    private fun requiredRuntimePermissions(role: Role): Array<String> {
        val p = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT >= 31) p += Manifest.permission.BLUETOOTH_CONNECT
        if (role == Role.SERVER) {
            p += Manifest.permission.READ_PHONE_STATE
            p += Manifest.permission.ANSWER_PHONE_CALLS
            p += Manifest.permission.READ_CONTACTS
            p += Manifest.permission.CALL_PHONE
        }
        return p.toTypedArray()
    }

    private fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    /** (name, MAC) of every paired device. Empty until the Bluetooth permission is granted. */
    @SuppressLint("MissingPermission")
    private fun bondedDevices(): List<Pair<String, String>> {
        if (!hasBluetoothPermission()) return emptyList()
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return runCatching {
            adapter.bondedDevices.map { (it.name ?: "Unknown") to it.address }.sortedBy { it.first }
        }.getOrDefault(emptyList())
    }

    /** Server-only: notification-listener access + default dialer role. */
    private fun requestServerRoles(launch: (Intent) -> Unit) {
        if (!isNotificationListenerEnabled()) {
            Toast.makeText(this, "Enable PhoneBridge, then come back", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        val rm = getSystemService(RoleManager::class.java)
        if (rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)) {
            launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        } else {
            proceed(Role.SERVER)
        }
    }

    private fun isNotificationListenerEnabled(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun proceed(role: Role) {
        if (role == Role.CLIENT && Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }
        val svc = if (role == Role.SERVER) ServerService::class.java else ClientService::class.java
        ContextCompat.startForegroundService(this, Intent(this, svc))
        Toast.makeText(this, "${role.name} started", Toast.LENGTH_SHORT).show()
    }

    private fun stopAll() {
        stopService(Intent(this, ServerService::class.java))
        stopService(Intent(this, ClientService::class.java))
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryExemption() {
        val pm = getSystemService(PowerManager::class.java)
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            )
        }
    }
}
