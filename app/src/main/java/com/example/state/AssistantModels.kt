package com.example.state

import com.example.config.AKLIMA_VOICE_NAME

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class VisualState {
    IDLE,
    THINKING,
    SPEAKING
}

fun deriveVisualState(assistantState: AssistantState, connectionState: ConnectionState): VisualState {
    if (connectionState != ConnectionState.CONNECTED && assistantState != AssistantState.THINKING) {
        return VisualState.IDLE
    }
    return when (assistantState) {
        AssistantState.THINKING -> VisualState.THINKING
        AssistantState.SPEAKING -> VisualState.SPEAKING
        AssistantState.IDLE,
        AssistantState.LISTENING,
        AssistantState.ERROR -> VisualState.IDLE
    }
}

enum class ScreenShareState {
    OFF,
    REQUESTING,
    STARTING,
    ACTIVE,
    PAUSED,
    STOPPING,
    ERROR
}

enum class ChatRole {
    USER_VOICE,
    USER_TEXT,
    ASSISTANT
}

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val isSpeakingNow: Boolean = false
)

enum class FrameQuality(val label: String, val maxDim: Int, val jpegQuality: Int) {
    AUTO("Auto", 896, 55),
    BALANCED("Balanced", 1024, 60),
    HIGH("High", 1024, 78)
}

enum class FrameRateMode(val label: String, val intervalMs: Long) {
    LOW("Low (0.5 fps)", 2_000L),
    BALANCED("Balanced (1 fps)", 1_000L)
}

data class SettingsState(
    val selectedCharacterId: String = "aklima",
    val readRepliesAloud: Boolean = true,
    val animationEnabled: Boolean = true,
    val reducedMotion: Boolean = false,
    val screenFrameQuality: FrameQuality = FrameQuality.BALANCED,
    val screenFrameRate: FrameRateMode = FrameRateMode.BALANCED
)

sealed class AppError(val userMessage: String) {
    class MicrophonePermissionDenied :
        AppError("Microphone permission is needed to talk to Aklima AI.")

    class AudioContextError(detail: String = "") :
        AppError("Audio device could not start${if (detail.isNotBlank()) ": $detail" else "."}")

    class GeminiConnectionError(detail: String = "") :
        AppError("Connection lost. Tap to reconnect.${if (detail.isNotBlank()) " ($detail)" else ""}")

    class SessionTimeout :
        AppError("Session timed out. Reconnecting with Aklima AI...")

    class ToolExecutionError(detail: String = "") :
        AppError("Could not complete action${if (detail.isNotBlank()) ": $detail" else "."}")

    class InvalidToolArguments :
        AppError("The requested action parameters were invalid.")

    class NetworkError :
        AppError("Network unavailable. Check your connection and try again.")

    class ScreenShareError(message: String) :
        AppError(message)

    class UnknownError(detail: String = "") :
        AppError("Something went wrong${if (detail.isNotBlank()) ": $detail" else "."}")
}

data class AssistantUiSnapshot(
    val assistantState: AssistantState = AssistantState.IDLE,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val isMicActive: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val micAmplitude: Float = 0f,
    val outputAmplitude: Float = 0f,
    val activeVoiceName: String = AKLIMA_VOICE_NAME,
    val errorMessage: String? = null,
    val hasResumptionHandle: Boolean = false
)
