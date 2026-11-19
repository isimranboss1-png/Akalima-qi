package com.example

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.live.LiveSessionManager
import com.example.reliability.ReliabilityMonitor
import com.example.reliability.ReliabilitySnapshot
import com.example.screenshare.ScreenShareManager
import com.example.state.AppError
import com.example.state.AssistantStore
import com.example.state.AssistantUiSnapshot
import com.example.state.ChatMessage
import com.example.state.ChatStore
import com.example.state.ConnectionState
import com.example.state.FrameQuality
import com.example.state.FrameRateMode
import com.example.state.ScreenShareSnapshot
import com.example.state.ScreenShareStore
import com.example.state.SettingsState
import com.example.state.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    val assistantStore = AssistantStore()
    val chatStore = ChatStore()
    val screenShareStore = ScreenShareStore()
    val settingsStore = SettingsStore(appContext)
    val reliabilityMonitor = ReliabilityMonitor(assistantStore, screenShareStore)

    private lateinit var screenShareManager: ScreenShareManager

    val liveSessionManager: LiveSessionManager = LiveSessionManager(
        context = appContext,
        scope = viewModelScope,
        assistantStore = assistantStore,
        chatStore = chatStore,
        screenShareStore = screenShareStore,
        reliabilityMonitor = reliabilityMonitor,
        onRequestLatestScreenFrame = {
            if (::screenShareManager.isInitialized) {
                screenShareManager.requestForcedFrameSend()
            }
        }
    )

    init {
        screenShareManager = ScreenShareManager(
            context = appContext,
            scope = viewModelScope,
            screenShareStore = screenShareStore,
            assistantStore = assistantStore,
            settingsStore = settingsStore,
            liveSessionManager = liveSessionManager,
            reliabilityMonitor = reliabilityMonitor
        )

        viewModelScope.launch {
            settingsStore.settings.collect { settings ->
                liveSessionManager.setReadRepliesAloud(settings.readRepliesAloud)
                liveSessionManager.setCharacter(settings.selectedCharacterId)
            }
        }
    }

    val assistantState: StateFlow<AssistantUiSnapshot> = assistantStore.state
    val chatMessages: StateFlow<List<ChatMessage>> = chatStore.messages
    val screenShareState: StateFlow<ScreenShareSnapshot> = screenShareStore.state
    val settingsState: StateFlow<SettingsState> = settingsStore.settings
    val reliabilityState: StateFlow<ReliabilitySnapshot> = reliabilityMonitor.snapshot

    private val _isChatOpen = MutableStateFlow(false)
    val isChatOpen: StateFlow<Boolean> = _isChatOpen.asStateFlow()

    private val _isSettingsOpen = MutableStateFlow(false)
    val isSettingsOpen: StateFlow<Boolean> = _isSettingsOpen.asStateFlow()

    private val _showScreenShareDialog = MutableStateFlow(false)
    val showScreenShareDialog: StateFlow<Boolean> = _showScreenShareDialog.asStateFlow()

    @Volatile
    private var videoReady: Boolean = false

    init {
        reliabilityMonitor.startMonitoring(
            scope = viewModelScope,
            isAudioOutputHealthy = { liveSessionManager.audioPlaybackManager.isHealthy() },
            recoverAudioOutput = { liveSessionManager.audioPlaybackManager.recover() },
            isVideoReady = { videoReady },
            onReconnectRequested = { liveSessionManager.reconnectNow() }
        )
    }

    fun onVideoReadyChanged(ready: Boolean) {
        videoReady = ready
    }

    fun onMicPermissionResult(granted: Boolean, startAfterGrant: Boolean) {
        assistantStore.setMicPermissionGranted(granted)
        if (granted && startAfterGrant) {
            startVoiceInteraction()
        } else if (!granted) {
            assistantStore.setError(AppError.MicrophonePermissionDenied())
        }
    }

    fun startVoiceInteraction() {
        viewModelScope.launch {
            if (assistantState.value.connectionState != ConnectionState.CONNECTED) {
                val ok = liveSessionManager.connect(resumeIfPossible = true)
                if (!ok) return@launch
            }
            liveSessionManager.startMicrophone()
        }
    }

    fun toggleMicrophone(hasMicPermission: Boolean, requestMicPermission: () -> Unit) {
        val snapshot = assistantState.value
        if (snapshot.isMicActive) {
            // Stop microphone only; keep Live session alive so text chat continues working
            liveSessionManager.stopMicrophone()
            return
        }
        if (!hasMicPermission) {
            requestMicPermission()
            return
        }
        if (snapshot.errorMessage != null) {
            assistantStore.clearError()
        }
        startVoiceInteraction()
    }

    fun connectOnly() {
        viewModelScope.launch {
            liveSessionManager.connect(resumeIfPossible = true)
        }
    }

    fun disconnectSession() {
        viewModelScope.launch {
            liveSessionManager.disconnect()
        }
    }

    fun sendChatMessage(text: String) {
        liveSessionManager.sendText(text)
    }

    fun clearChat() {
        chatStore.clear()
    }

    fun openChat() {
        _isChatOpen.value = true
    }

    fun closeChat() {
        _isChatOpen.value = false
    }

    fun openSettings() {
        _isSettingsOpen.value = true
    }

    fun closeSettings() {
        _isSettingsOpen.value = false
    }

    fun requestScreenShareConfirmation() {
        _showScreenShareDialog.value = true
    }

    fun dismissScreenShareConfirmation() {
        _showScreenShareDialog.value = false
    }

    fun onScreenSharePermissionResult(resultCode: Int, data: Intent?) {
        if (data != null && resultCode == android.app.Activity.RESULT_OK) {
            screenShareManager.onPermissionGranted(resultCode, data)
        } else {
            screenShareManager.onPermissionDenied(cancelled = resultCode == android.app.Activity.RESULT_CANCELED)
        }
    }

    fun stopScreenShare() {
        screenShareManager.stopSharing()
    }

    fun clearScreenShareNotice() {
        screenShareStore.clearNotice()
    }

    fun setReadRepliesAloud(enabled: Boolean) = settingsStore.setReadRepliesAloud(enabled)
    fun setAnimationEnabled(enabled: Boolean) = settingsStore.setAnimationEnabled(enabled)
    fun setReducedMotion(enabled: Boolean) = settingsStore.setReducedMotion(enabled)
    fun setScreenFrameQuality(quality: FrameQuality) = settingsStore.setScreenFrameQuality(quality)
    fun setScreenFrameRate(rate: FrameRateMode) = settingsStore.setScreenFrameRate(rate)
    fun selectCharacter(id: String) = settingsStore.selectCharacter(id)

    fun runManualHealthCheck() {
        reliabilityMonitor.runManualCheck(
            scope = viewModelScope,
            isAudioOutputHealthy = { liveSessionManager.audioPlaybackManager.isHealthy() },
            recoverAudioOutput = { liveSessionManager.audioPlaybackManager.recover() },
            isVideoReady = { videoReady },
            onReconnectRequested = { liveSessionManager.reconnectNow() }
        )
    }

    fun onAppPaused() {
        screenShareManager.onAppPaused()
    }

    fun onAppResumed() {
        screenShareManager.onAppResumed()
    }

    override fun onCleared() {
        reliabilityMonitor.stopMonitoring()
        screenShareManager.release()
        liveSessionManager.release()
        super.onCleared()
    }
}
