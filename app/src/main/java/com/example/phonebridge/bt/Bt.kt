package com.example.phonebridge.bt

import java.util.UUID

object Bt {
    /** Fixed service UUID: the Server listens on it, the Client connects to it. */
    val UUID: UUID = java.util.UUID.fromString("8f1e2b1a-3c4d-4e5f-9a6b-7c8d9e0f1a2b")
    const val SERVICE_NAME = "PhoneBridge"

    const val FRAME_TEXT: Int = 0    // UTF-8 JSON
    const val FRAME_AUDIO: Int = 1   // raw PCM16 16 kHz mono, 20 ms
    const val MAX_FRAME = 4 * 1024 * 1024
}
