package com.example.phonebridge

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun CallScreen(
    info: CallInfo,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    footer: String? = null
) {
    val label = when (info.state) {
        "ringing" -> "Incoming call"
        "dialing" -> "Calling…"
        "holding" -> "On hold"
        else -> "In call"
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.padding(top = 72.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Text(info.title, style = MaterialTheme.typography.headlineLarge)
            if (info.name.isNotBlank() && info.number.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(info.number, style = MaterialTheme.typography.titleMedium)
            }
        }
        if (footer != null) Text(footer, style = MaterialTheme.typography.bodyMedium)
        Row(
            Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (info.state == "ringing") {
                CallButton("Decline", Color(0xFFC62828), onDecline)
                CallButton("Answer", Color(0xFF2E7D32), onAnswer)
            } else {
                CallButton("Hang up", Color(0xFFC62828), onHangUp)
            }
        }
    }
}

@Composable
private fun CallButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color),
        modifier = Modifier.height(64.dp).widthIn(min = 128.dp)
    ) { Text(text) }
}
