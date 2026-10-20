package com.example.phonebridge.server

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

class DialerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preset = intent?.data?.schemeSpecificPart.orEmpty()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    var number by remember { mutableStateOf(preset) }
                    Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Dialer", style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(
                            value = number,
                            onValueChange = { number = it.filter { c -> c.isDigit() || c in "+*#" } },
                            label = { Text("Number") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { call(number) },
                            enabled = number.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Call") }
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun call(number: String) {
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Grant the Phone permission first", Toast.LENGTH_LONG).show()
            return
        }
        getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts("tel", number, null), Bundle())
        startActivity(Intent(this, InCallActivity::class.java))
        finish()
    }
}
