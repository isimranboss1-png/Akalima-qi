package com.example.config

object AppConstants {
    const val APP_NAME = "Agents AI"
    const val APP_VERSION = "1.0.0"

    // Gemini Live API model verified on Firebase AI Logic (Google AI backend)
    const val LIVE_MODEL_NAME = "gemini-3.1-flash-live-preview"

    // Audio formats: 16 kHz PCM16 mono input -> 24 kHz PCM16 mono output
    const val INPUT_SAMPLE_RATE_HZ = 16_000
    const val OUTPUT_SAMPLE_RATE_HZ = 24_000
    const val AUDIO_CHANNELS = 1

    // Chat memory constraints (in-memory only, never persisted)
    const val MAX_CHAT_MESSAGES = 30

    // Screen share frame constants
    const val SCREEN_CAPTURE_FPS = 2
    const val SCREEN_FRAME_INTERVAL_MS = 1_000L
    const val MAX_SCREEN_IMAGE_WIDTH = 1024
    const val MAX_SCREEN_IMAGE_HEIGHT = 1024
    const val SCREEN_JPEG_QUALITY = 60
    const val SCREEN_CHANGE_THRESHOLD = 0.02f
    const val SCREEN_FRAME_STALE_MS = 8_000L

    // Reconnect & reliability constants
    const val CONNECT_TIMEOUT_MS = 20_000L
    const val MAX_RECONNECT_ATTEMPTS = 4
    const val HEALTH_CHECK_INTERVAL_MS = 60_000L
    const val DEEP_INSPECTION_INTERVAL_MS = 300_000L
    const val MAX_AUDIT_LOG_ENTRIES = 60
}
