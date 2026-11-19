package com.example.state

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.config.AKLIMA_VOICE_NAME
import com.example.config.AppConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class AssistantStore {
    private val _state = MutableStateFlow(AssistantUiSnapshot())
    val state: StateFlow<AssistantUiSnapshot> = _state.asStateFlow()

    fun setConnectionState(connectionState: ConnectionState) {
        _state.update {
            it.copy(
                connectionState = connectionState,
                errorMessage = if (connectionState == ConnectionState.CONNECTED) null else it.errorMessage
            )
        }
    }

    fun setAssistantState(assistantState: AssistantState) {
        _state.update { it.copy(assistantState = assistantState) }
    }

    fun setMicActive(active: Boolean) {
        _state.update {
            val nextAssistant = when {
                active && it.assistantState == AssistantState.IDLE -> AssistantState.LISTENING
                !active && it.assistantState == AssistantState.LISTENING -> AssistantState.IDLE
                else -> it.assistantState
            }
            it.copy(
                isMicActive = active,
                micAmplitude = if (active) it.micAmplitude else 0f,
                assistantState = nextAssistant
            )
        }
    }

    fun setMicPermissionGranted(granted: Boolean) {
        _state.update { it.copy(micPermissionGranted = granted) }
    }

    fun updateMicAmplitude(level: Float) {
        _state.update { it.copy(micAmplitude = level.coerceIn(0f, 1f)) }
    }

    fun updateOutputAmplitude(level: Float) {
        _state.update { it.copy(outputAmplitude = level.coerceIn(0f, 1f)) }
    }

    fun setActiveVoiceName(voiceName: String) {
        _state.update { it.copy(activeVoiceName = voiceName.ifBlank { AKLIMA_VOICE_NAME }) }
    }

    fun setHasResumptionHandle(hasHandle: Boolean) {
        _state.update { it.copy(hasResumptionHandle = hasHandle) }
    }

    fun setError(error: AppError?) {
        _state.update {
            it.copy(
                errorMessage = error?.userMessage,
                assistantState = if (error != null) AssistantState.ERROR else AssistantState.IDLE
            )
        }
    }

    fun clearError() {
        _state.update {
            it.copy(
                errorMessage = null,
                assistantState = if (it.isMicActive) AssistantState.LISTENING else AssistantState.IDLE
            )
        }
    }
}

/**
 * In-memory only chat store. Never persisted to disk, DataStore, or remote servers.
 */
class ChatStore {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private var activeUserVoiceId: String? = null
    private var activeAssistantId: String? = null

    fun appendUserText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        finalizeStreamingTurn()
        val msg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER_TEXT,
            text = trimmed
        )
        pushMessage(msg)
    }

    fun appendUserVoiceTranscript(chunk: String) {
        if (chunk.isBlank()) return
        _messages.update { list ->
            val existingId = activeUserVoiceId
            if (existingId != null) {
                list.map { msg ->
                    if (msg.id == existingId) {
                        msg.copy(text = mergeTranscript(msg.text, chunk), isStreaming = true)
                    } else {
                        msg
                    }
                }
            } else {
                val id = UUID.randomUUID().toString()
                activeUserVoiceId = id
                trimHistory(
                    list + ChatMessage(
                        id = id,
                        role = ChatRole.USER_VOICE,
                        text = chunk.trimStart(),
                        isStreaming = true
                    )
                )
            }
        }
    }

    fun appendAssistantTranscript(chunk: String, isSpeakingNow: Boolean) {
        if (chunk.isBlank()) return
        // User finished speaking once assistant responds
        finalizeUserVoice()
        _messages.update { list ->
            val existingId = activeAssistantId
            if (existingId != null) {
                list.map { msg ->
                    if (msg.id == existingId) {
                        msg.copy(
                            text = mergeTranscript(msg.text, chunk),
                            isStreaming = true,
                            isSpeakingNow = isSpeakingNow
                        )
                    } else {
                        msg.copy(isSpeakingNow = false)
                    }
                }
            } else {
                val id = UUID.randomUUID().toString()
                activeAssistantId = id
                trimHistory(
                    list.map { it.copy(isSpeakingNow = false) } + ChatMessage(
                        id = id,
                        role = ChatRole.ASSISTANT,
                        text = chunk.trimStart(),
                        isStreaming = true,
                        isSpeakingNow = isSpeakingNow
                    )
                )
            }
        }
    }

    fun updateAssistantSpeakingState(isSpeaking: Boolean) {
        _messages.update { list ->
            val currentId = activeAssistantId ?: return@update list.map { it.copy(isSpeakingNow = false) }
            list.map { msg ->
                if (msg.id == currentId) msg.copy(isSpeakingNow = isSpeaking)
                else msg.copy(isSpeakingNow = false)
            }
        }
    }

    fun finalizeStreamingTurn() {
        finalizeUserVoice()
        activeAssistantId = null
        _messages.update { list ->
            list.map { it.copy(isStreaming = false, isSpeakingNow = false) }
        }
    }

    private fun finalizeUserVoice() {
        activeUserVoiceId = null
        _messages.update { list ->
            list.map { if (it.role == ChatRole.USER_VOICE) it.copy(isStreaming = false) else it }
        }
    }

    fun clear() {
        activeUserVoiceId = null
        activeAssistantId = null
        _messages.value = emptyList()
    }

    private fun pushMessage(msg: ChatMessage) {
        _messages.update { trimHistory(it + msg) }
    }

    private fun trimHistory(list: List<ChatMessage>): List<ChatMessage> {
        return if (list.size > AppConstants.MAX_CHAT_MESSAGES) {
            list.takeLast(AppConstants.MAX_CHAT_MESSAGES)
        } else {
            list
        }
    }

    private fun mergeTranscript(existing: String, incoming: String): String {
        if (existing.isEmpty()) return incoming.trimStart()
        val needsSpace = !existing.endsWith(" ") &&
            !incoming.startsWith(" ") &&
            !incoming.first().let { it in listOf('.', ',', '!', '?', ':', ';', '।') }
        return if (needsSpace) "$existing $incoming" else "$existing$incoming"
    }
}

data class ScreenShareSnapshot(
    val state: ScreenShareState = ScreenShareState.OFF,
    val statusNotice: String? = null,
    val errorMessage: String? = null,
    val framesSentCount: Int = 0,
    val lastFrameSentAtMs: Long = 0L
)

class ScreenShareStore {
    private val _state = MutableStateFlow(ScreenShareSnapshot())
    val state: StateFlow<ScreenShareSnapshot> = _state.asStateFlow()

    fun setState(newState: ScreenShareState, notice: String? = null) {
        _state.update {
            it.copy(
                state = newState,
                statusNotice = notice,
                errorMessage = if (newState != ScreenShareState.ERROR) null else it.errorMessage
            )
        }
    }

    fun setError(message: String) {
        _state.update {
            it.copy(
                state = ScreenShareState.ERROR,
                errorMessage = message,
                statusNotice = null
            )
        }
    }

    fun clearNotice() {
        _state.update { it.copy(statusNotice = null, errorMessage = null) }
    }

    fun recordFrameSent(timestampMs: Long) {
        _state.update {
            it.copy(
                framesSentCount = it.framesSentCount + 1,
                lastFrameSentAtMs = timestampMs
            )
        }
    }

    fun resetSessionStats() {
        _state.update {
            it.copy(framesSentCount = 0, lastFrameSentAtMs = 0L)
        }
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "agents_ai_settings")

class SettingsStore(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _settings = MutableStateFlow(SettingsState())
    val settings: StateFlow<SettingsState> = _settings.asStateFlow()

    private object Keys {
        val CHARACTER_ID = stringPreferencesKey("character_id")
        val READ_ALIVE = booleanPreferencesKey("read_replies_aloud")
        val ANIMATION_ENABLED = booleanPreferencesKey("animation_enabled")
        val REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
        val FRAME_QUALITY = stringPreferencesKey("screen_frame_quality")
        val FRAME_RATE = stringPreferencesKey("screen_frame_rate")
    }

    init {
        scope.launch {
            appContext.settingsDataStore.data.map { prefs ->
                SettingsState(
                    selectedCharacterId = prefs[Keys.CHARACTER_ID] ?: "aklima",
                    readRepliesAloud = prefs[Keys.READ_ALIVE] ?: true,
                    animationEnabled = prefs[Keys.ANIMATION_ENABLED] ?: true,
                    reducedMotion = prefs[Keys.REDUCED_MOTION] ?: false,
                    screenFrameQuality = prefs[Keys.FRAME_QUALITY]?.let { name ->
                        FrameQuality.entries.firstOrNull { it.name == name }
                    } ?: FrameQuality.BALANCED,
                    screenFrameRate = prefs[Keys.FRAME_RATE]?.let { name ->
                        FrameRateMode.entries.firstOrNull { it.name == name }
                    } ?: FrameRateMode.BALANCED
                )
            }.collect { loaded ->
                _settings.value = loaded
            }
        }
    }

    fun setReadRepliesAloud(enabled: Boolean) {
        _settings.update { it.copy(readRepliesAloud = enabled) }
        scope.launch {
            appContext.settingsDataStore.edit { it[Keys.READ_ALIVE] = enabled }
        }
    }

    fun setAnimationEnabled(enabled: Boolean) {
        _settings.update { it.copy(animationEnabled = enabled) }
        scope.launch {
            appContext.settingsDataStore.edit { it[Keys.ANIMATION_ENABLED] = enabled }
        }
    }

    fun setReducedMotion(enabled: Boolean) {
        _settings.update { it.copy(reducedMotion = enabled) }
        scope.launch {
            appContext.settingsDataStore.edit { it[Keys.REDUCED_MOTION] = enabled }
        }
    }

    fun setScreenFrameQuality(quality: FrameQuality) {
        _settings.update { it.copy(screenFrameQuality = quality) }
        scope.launch {
            appContext.settingsDataStore.edit { it[Keys.FRAME_QUALITY] = quality.name }
        }
    }

    fun setScreenFrameRate(rate: FrameRateMode) {
        _settings.update { it.copy(screenFrameRate = rate) }
        scope.launch {
            appContext.settingsDataStore.edit { it[Keys.FRAME_RATE] = rate.name }
        }
    }

    fun selectCharacter(characterId: String) {
        _settings.update { it.copy(selectedCharacterId = characterId) }
        scope.launch {
            appContext.settingsDataStore.edit { it[Keys.CHARACTER_ID] = characterId }
        }
    }
}
