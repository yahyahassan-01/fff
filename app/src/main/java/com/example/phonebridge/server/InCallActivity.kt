package com.example.phonebridge.server

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.phonebridge.CallScreen

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    val info by ServerHub.callInfo.collectAsState()
                    val level by ServerHub.micLevel.collectAsState()
                    val micStatus by ServerHub.micStatus.collectAsState()
                    LaunchedEffect(info == null) { if (info == null) finish() }
                    info?.let {
                        CallScreen(
                            it,
                            onAnswer = { BridgeInCallService.instance?.answerRinging() },
                            onDecline = { BridgeInCallService.instance?.rejectRinging() },
                            onHangUp = { BridgeInCallService.instance?.hangUp() },
                            footer = if (it.state == "active" && micStatus.isNotEmpty())
                                "$micStatus  |  pickup level: $level" else null
                        )
                    }
                }
            }
        }
    }
}
