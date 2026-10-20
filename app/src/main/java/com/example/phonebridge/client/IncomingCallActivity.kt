package com.example.phonebridge.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.phonebridge.CallScreen
import com.example.phonebridge.Msg

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)    // draw above the keyguard without unlocking
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val info by ClientHub.callInfo.collectAsState()
                    LaunchedEffect(info == null) { if (info == null) finish() }
                    info?.let {
                        CallScreen(
                            it,
                            onAnswer = { ClientHub.send(Msg.CALL_ANSWER) },
                            onDecline = { ClientHub.send(Msg.CALL_DECLINE) },
                            onHangUp = { ClientHub.send(Msg.CALL_HANGUP) }
                        )
                    }
                }
            }
        }
    }
}
