package com.example.live

import android.content.Context
import android.util.Log
import com.example.audio.AudioCaptureManager
import com.example.audio.AudioPlaybackManager
import com.example.config.AKLIMA_VOICE_NAME
import com.example.config.AppConstants
import com.example.config.CharacterRegistry
import com.example.reliability.AuditEventType
import com.example.reliability.HealthSeverity
import com.example.reliability.ReliabilityMonitor
import com.example.state.AppError
import com.example.state.AssistantState
import com.example.state.AssistantStore
import com.example.state.ChatStore
import com.example.state.ConnectionState
import com.example.state.ScreenShareState
import com.example.state.ScreenShareStore
import com.example.tools.ToolManager
import com.google.firebase.ai.type.InlineData
import com.google.firebase.ai.type.InlineDataPart
import com.google.firebase.ai.type.LiveServerContent
import com.google.firebase.ai.type.LiveServerGoAway
import com.google.firebase.ai.type.LiveServerToolCall
import com.google.firebase.ai.type.LiveServerToolCallCancellation
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.LiveSessionResumptionUpdate
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.SessionResumptionConfig
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

private const val TAG = "LiveSessionManager"

@OptIn(PublicPreviewAPI::class)
class LiveSessionManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val assistantStore: AssistantStore,
    private val chatStore: ChatStore,
    private val screenShareStore: ScreenShareStore,
    private val reliabilityMonitor: ReliabilityMonitor,
    private val onRequestLatestScreenFrame: () -> Unit
) {
    private val connectMutex = Mutex()
    private val audioCaptureManager = AudioCaptureManager()
    val audioPlaybackManager = AudioPlaybackManager(
        onSpeakingStateChanged = { speaking ->
            handlePlaybackSpeakingChanged(speaking)
        },
        onAmplitude = { level ->
            assistantStore.updateOutputAmplitude(level)
        }
    )

    private var liveSession: LiveSession? = null
    private var receiveJob: Job? = null
    private var activeCharacterId: String = CharacterRegistry.aklima.id
    private var resumptionHandle: String? = null
    private var reconnectAttempts: Int = 0
    private var userInitiatedDisconnect: Boolean = false

    @Volatile
    private var isAssistantSpeaking: Boolean = false

    init {
        audioPlaybackManager.start(scope)
    }

    fun setCharacter(characterId: String) {
        val profile = CharacterRegistry.getById(characterId)
        activeCharacterId = profile.id
        assistantStore.setActiveVoiceName(profile.voiceName)
    }

    fun setReadRepliesAloud(enabled: Boolean) {
        audioPlaybackManager.setMuted(!enabled)
    }

    suspend fun connect(resumeIfPossible: Boolean = true): Boolean = connectMutex.withLock {
        val existing = liveSession
        if (existing != null && !existing.isClosed() &&
            assistantStore.state.value.connectionState == ConnectionState.CONNECTED
        ) {
            return true
        }

        userInitiatedDisconnect = false
        assistantStore.setConnectionState(ConnectionState.CONNECTING)
        assistantStore.clearError()

        val profile = CharacterRegistry.getById(activeCharacterId)
        assistantStore.setActiveVoiceName(profile.voiceName)
        Log.i(TAG, "Connecting LiveSession with voiceName=${profile.voiceName}")

        return try {
            val model = LiveConfigBuilder.buildLiveModel(profile)
            val resumptionConfig = if (resumeIfPossible && !resumptionHandle.isNullOrBlank()) {
                SessionResumptionConfig(handle = resumptionHandle!!)
            } else {
                SessionResumptionConfig()
            }

            val session = withTimeout(AppConstants.CONNECT_TIMEOUT_MS) {
                model.connect(sessionResumption = resumptionConfig)
            }

            liveSession = session
            reconnectAttempts = 0
            assistantStore.setConnectionState(ConnectionState.CONNECTED)
            assistantStore.setAssistantState(
                if (audioCaptureManager.isCapturing) AssistantState.LISTENING else AssistantState.IDLE
            )

            startReceiving(session)
            reliabilityMonitor.recordAudit(
                type = AuditEventType.HEALTH_CHECK,
                title = "Live Session Connected",
                detail = "Model=${AppConstants.LIVE_MODEL_NAME}, Voice=${profile.voiceName}",
                severity = HealthSeverity.OK
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "LiveSession connect failed", e)
            val err = mapError(e)
            assistantStore.setConnectionState(ConnectionState.DISCONNECTED)
            assistantStore.setError(err)
            reliabilityMonitor.recordAudit(
                type = AuditEventType.INCIDENT,
                title = "Connection Failed",
                detail = err.userMessage,
                severity = HealthSeverity.WARNING,
                isIncident = true
            )
            false
        }
    }

    suspend fun disconnect() = connectMutex.withLock {
        userInitiatedDisconnect = true
        stopMicrophoneInternal()
        audioPlaybackManager.interrupt()
        receiveJob?.cancel()
        receiveJob = null
        val session = liveSession
        liveSession = null
        if (session != null && !session.isClosed()) {
            runCatching { session.close() }
        }
        chatStore.finalizeStreamingTurn()
        assistantStore.setConnectionState(ConnectionState.DISCONNECTED)
        assistantStore.setAssistantState(AssistantState.IDLE)
    }

    fun startMicrophone(): Boolean {
        if (audioCaptureManager.isCapturing) return true
        val started = audioCaptureManager.start(
            scope = scope,
            onChunk = { pcmBytes ->
                sendAudioChunk(pcmBytes)
            },
            onAmplitude = { level ->
                assistantStore.updateMicAmplitude(level)
            },
            onSpeechActivity = {
                if (screenShareStore.state.value.state == ScreenShareState.ACTIVE) {
                    onRequestLatestScreenFrame()
                }
            },
            onError = { appError ->
                assistantStore.setMicActive(false)
                assistantStore.setError(appError)
            }
        )
        assistantStore.setMicActive(started)
        if (started && assistantStore.state.value.assistantState == AssistantState.IDLE) {
            assistantStore.setAssistantState(AssistantState.LISTENING)
        }
        return started
    }

    fun stopMicrophone() {
        stopMicrophoneInternal()
    }

    private fun stopMicrophoneInternal() {
        audioCaptureManager.stop()
        assistantStore.setMicActive(false)
        if (assistantStore.state.value.assistantState == AssistantState.LISTENING) {
            assistantStore.setAssistantState(AssistantState.IDLE)
        }
    }

    fun sendText(rawText: String) {
        val text = rawText.trim()
        if (text.isEmpty()) return

        scope.launch {
            if (isAssistantSpeaking || assistantStore.state.value.assistantState == AssistantState.SPEAKING) {
                interrupt()
            }

            chatStore.appendUserText(text)
            assistantStore.setAssistantState(AssistantState.THINKING)

            val connected = connect(resumeIfPossible = true)
            if (!connected) {
                assistantStore.setError(AppError.GeminiConnectionError())
                return@launch
            }

            if (screenShareStore.state.value.state == ScreenShareState.ACTIVE) {
                onRequestLatestScreenFrame()
                delay(80)
            }

            val session = liveSession
            if (session == null || session.isClosed()) {
                assistantStore.setError(AppError.GeminiConnectionError())
                return@launch
            }

            try {
                session.sendTextRealtime(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "sendTextRealtime failed, trying content send", e)
                try {
                    session.send(text, turnComplete = true)
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed sending text to LiveSession", e2)
                    assistantStore.setError(mapError(e2))
                }
            }
        }
    }

    suspend fun sendScreenFrame(jpegBytes: ByteArray) {
        val session = liveSession ?: return
        if (session.isClosed()) return
        if (assistantStore.state.value.connectionState != ConnectionState.CONNECTED) return

        try {
            session.sendVideoRealtime(InlineData(jpegBytes, "image/jpeg"))
            screenShareStore.recordFrameSent(System.currentTimeMillis())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sendVideoRealtime failed: ${e.localizedMessage}")
        }
    }

    fun sendContextNote(note: String) {
        scope.launch {
            val session = liveSession ?: return@launch
            if (session.isClosed()) return@launch
            if (assistantStore.state.value.connectionState != ConnectionState.CONNECTED) return@launch
            runCatching {
                session.send(
                    content = content("user") { text(note) },
                    turnComplete = false
                )
            }.onFailure { e ->
                Log.w(TAG, "Non-spoken context note skipped: ${e.localizedMessage}")
            }
        }
    }

    fun interrupt() {
        audioPlaybackManager.interrupt()
        isAssistantSpeaking = false
        chatStore.updateAssistantSpeakingState(false)
        chatStore.finalizeStreamingTurn()
        assistantStore.setAssistantState(
            if (audioCaptureManager.isCapturing) AssistantState.LISTENING else AssistantState.IDLE
        )
    }

    fun reconnectNow() {
        scope.launch {
            if (userInitiatedDisconnect) return@launch
            liveSession?.let { old -> runCatching { old.close() } }
            liveSession = null
            receiveJob?.cancel()
            connect(resumeIfPossible = true)
        }
    }

    private suspend fun sendAudioChunk(pcmBytes: ByteArray) {
        val session = liveSession ?: return
        if (session.isClosed()) return
        if (assistantStore.state.value.connectionState != ConnectionState.CONNECTED) return

        try {
            session.sendAudioRealtime(InlineData(pcmBytes, "audio/pcm;rate=16000"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sendAudioRealtime error: ${e.localizedMessage}")
        }
    }

    private fun startReceiving(session: LiveSession) {
        receiveJob?.cancel()
        receiveJob = scope.launch(Dispatchers.IO) {
            try {
                session.receive().collect { message ->
                    when (message) {
                        is LiveServerContent -> handleServerContent(session, message)
                        is LiveServerToolCall -> handleToolCalls(session, message)
                        is LiveServerToolCallCancellation -> {
                            Log.d(TAG, "Tool call cancelled: ${message.functionIds}")
                        }
                        is LiveSessionResumptionUpdate -> {
                            if (message.resumable == true && !message.newHandle.isNullOrBlank()) {
                                resumptionHandle = message.newHandle
                                assistantStore.setHasResumptionHandle(true)
                            }
                        }
                        is LiveServerGoAway -> {
                            Log.w(TAG, "Received LiveServerGoAway (timeLeft=${message.timeLeft}); reconnecting cleanly with female voice $AKLIMA_VOICE_NAME")
                            reliabilityMonitor.recordAudit(
                                AuditEventType.AUTO_REPAIR,
                                "Session Refresh Scheduled",
                                "Server GoAway received; resuming seamlessly with female voice $AKLIMA_VOICE_NAME.",
                                HealthSeverity.OK,
                                isRepair = true
                            )
                            launch {
                                delay(250)
                                reconnectNow()
                            }
                        }
                    }
                }
                // Flow ended normally
                if (!userInitiatedDisconnect) {
                    handleUnexpectedDisconnect("Session stream closed")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Receive stream error", e)
                if (!userInitiatedDisconnect) {
                    handleUnexpectedDisconnect(e.localizedMessage ?: "Stream disconnected")
                }
            }
        }
    }

    private fun handleServerContent(session: LiveSession, msg: LiveServerContent) {
        if (msg.interrupted) {
            Log.d(TAG, "Server signaled interruption")
            audioPlaybackManager.interrupt()
            isAssistantSpeaking = false
            chatStore.finalizeStreamingTurn()
            assistantStore.setAssistantState(
                if (audioCaptureManager.isCapturing) AssistantState.LISTENING else AssistantState.IDLE
            )
            return
        }

        msg.inputTranscription?.text?.let { inputText ->
            if (inputText.isNotBlank()) {
                chatStore.appendUserVoiceTranscript(inputText)
                if (!isAssistantSpeaking && assistantStore.state.value.assistantState == AssistantState.LISTENING) {
                    assistantStore.setAssistantState(AssistantState.THINKING)
                }
            }
        }

        val parts = msg.content?.parts.orEmpty()
        var hasAudioBytes = false
        for (part in parts) {
            if (part is InlineDataPart && part.mimeType.startsWith("audio/pcm")) {
                hasAudioBytes = true
                audioPlaybackManager.enqueue(part.inlineData)
            }
        }

        msg.outputTranscription?.text?.let { outputText ->
            if (outputText.isNotBlank()) {
                chatStore.appendAssistantTranscript(
                    chunk = outputText,
                    isSpeakingNow = isAssistantSpeaking || hasAudioBytes
                )
            }
        }

        if (msg.turnComplete || msg.generationComplete) {
            audioPlaybackManager.markTurnComplete()
            if (!isAssistantSpeaking && !hasAudioBytes) {
                chatStore.finalizeStreamingTurn()
                if (assistantStore.state.value.assistantState == AssistantState.THINKING) {
                    assistantStore.setAssistantState(
                        if (audioCaptureManager.isCapturing) AssistantState.LISTENING else AssistantState.IDLE
                    )
                }
            }
        }
    }

    private suspend fun handleToolCalls(session: LiveSession, toolCall: LiveServerToolCall) {
        val responses = toolCall.functionCalls.map { call ->
            ToolManager.executeToolCall(context, call) { auditMsg ->
                reliabilityMonitor.recordAudit(
                    type = AuditEventType.TOOL_ACTION,
                    title = "Tool Call: ${call.name}",
                    detail = auditMsg,
                    severity = HealthSeverity.OK
                )
            }
        }
        runCatching {
            session.sendFunctionResponse(responses)
        }.onFailure { e ->
            Log.e(TAG, "Failed to send tool function response", e)
        }
    }

    private fun handlePlaybackSpeakingChanged(speaking: Boolean) {
        isAssistantSpeaking = speaking
        chatStore.updateAssistantSpeakingState(speaking)
        if (speaking) {
            assistantStore.setAssistantState(AssistantState.SPEAKING)
        } else {
            chatStore.finalizeStreamingTurn()
            val current = assistantStore.state.value.assistantState
            if (current == AssistantState.SPEAKING || current == AssistantState.THINKING) {
                assistantStore.setAssistantState(
                    if (audioCaptureManager.isCapturing) AssistantState.LISTENING else AssistantState.IDLE
                )
            }
        }
    }

    private fun handleUnexpectedDisconnect(reason: String) {
        liveSession = null
        assistantStore.setConnectionState(ConnectionState.DISCONNECTED)
        audioPlaybackManager.interrupt()

        if (reconnectAttempts < AppConstants.MAX_RECONNECT_ATTEMPTS && !userInitiatedDisconnect) {
            reconnectAttempts++
            val backoffMs = 1000L * reconnectAttempts
            scope.launch {
                assistantStore.setConnectionState(ConnectionState.CONNECTING)
                delay(backoffMs)
                if (!userInitiatedDisconnect) {
                    val ok = connect(resumeIfPossible = true)
                    if (!ok && reconnectAttempts >= AppConstants.MAX_RECONNECT_ATTEMPTS) {
                        assistantStore.setError(AppError.GeminiConnectionError())
                    }
                }
            }
        } else {
            assistantStore.setError(AppError.GeminiConnectionError())
        }
    }

    private fun mapError(e: Throwable): AppError {
        val msg = e.localizedMessage.orEmpty()
        return when {
            msg.contains("403") || msg.contains("AppCheck", ignoreCase = true) ->
                AppError.GeminiConnectionError("App Check verification needed")
            msg.contains("timeout", ignoreCase = true) ->
                AppError.SessionTimeout()
            msg.contains("network", ignoreCase = true) || msg.contains("host", ignoreCase = true) ->
                AppError.NetworkError()
            else -> AppError.GeminiConnectionError()
        }
    }

    fun release() {
        userInitiatedDisconnect = true
        stopMicrophoneInternal()
        audioPlaybackManager.release()
        receiveJob?.cancel()
        liveSession?.let { s -> scope.launch { runCatching { s.close() } } }
        liveSession = null
    }
}
