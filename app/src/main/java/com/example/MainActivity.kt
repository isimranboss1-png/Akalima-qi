package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.config.CharacterRegistry
import com.example.security.configureAppCheck
import com.example.state.ScreenShareState
import com.example.state.deriveVisualState
import com.example.ui.components.BottomControlDock
import com.example.ui.components.ChatSheet
import com.example.ui.components.CharacterVideo
import com.example.ui.components.ScreenSharePrivacyDialog
import com.example.ui.components.SettingsDrawer
import com.example.ui.components.TopStatusBar
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mandatory Step 1: Configure Firebase App Check with launch intent before any UI or AI operations
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AgentsAiApp(viewModel = viewModel)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.onAppPaused()
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }
}

@Composable
private fun AgentsAiApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val assistant by viewModel.assistantState.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val screenShare by viewModel.screenShareState.collectAsStateWithLifecycle()
    val settings by viewModel.settingsState.collectAsStateWithLifecycle()
    val reliability by viewModel.reliabilityState.collectAsStateWithLifecycle()
    val isChatOpen by viewModel.isChatOpen.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val showScreenShareDialog by viewModel.showScreenShareDialog.collectAsStateWithLifecycle()

    var pendingStartMicOnGrant by remember { mutableStateOf(false) }

    val hasMicPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit) {
        viewModel.assistantStore.setMicPermissionGranted(hasMicPermission())
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicPermissionResult(granted, startAfterGrant = pendingStartMicOnGrant)
        pendingStartMicOnGrant = false
    }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onScreenSharePermissionResult(result.resultCode, result.data)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
        screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    fun launchSystemScreenShareConsent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val projectionManager = context.getSystemService(MediaProjectionManager::class.java)
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    val character = CharacterRegistry.getById(settings.selectedCharacterId)
    val visualState = deriveVisualState(assistant.assistantState, assistant.connectionState)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 700.dp
        val maxChatHeight = maxHeight * 0.45f

        // Full-screen Character Video Surface (always alive & unobscured)
        CharacterVideo(
            character = character,
            visualState = visualState,
            animationEnabled = settings.animationEnabled,
            reducedMotion = settings.reducedMotion,
            onVideoReadyChanged = viewModel::onVideoReadyChanged,
            modifier = Modifier.fillMaxSize()
        )

        // Top Bar
        TopStatusBar(
            connectionState = assistant.connectionState,
            screenShareState = screenShare.state,
            onStopScreenShare = viewModel::stopScreenShare,
            onOpenSettings = viewModel::openSettings,
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Voice-First Bottom Control Dock (hidden only on compact portrait while chat sheet covers bottom)
        AnimatedVisibility(
            visible = !isChatOpen || isWideLayout,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomControlDock(
                assistantState = assistant.assistantState,
                connectionState = assistant.connectionState,
                isMicActive = assistant.isMicActive,
                micAmplitude = assistant.micAmplitude,
                outputAmplitude = assistant.outputAmplitude,
                errorMessage = assistant.errorMessage,
                screenShareState = screenShare.state,
                screenShareNotice = screenShare.errorMessage ?: screenShare.statusNotice,
                hasChatMessages = chatMessages.isNotEmpty(),
                reducedMotion = settings.reducedMotion,
                onToggleMic = {
                    viewModel.toggleMicrophone(
                        hasMicPermission = hasMicPermission(),
                        requestMicPermission = {
                            pendingStartMicOnGrant = true
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                },
                onOpenChat = viewModel::openChat,
                onToggleScreenShare = {
                    val active = screenShare.state == ScreenShareState.ACTIVE ||
                        screenShare.state == ScreenShareState.PAUSED ||
                        screenShare.state == ScreenShareState.STARTING
                    if (active) {
                        viewModel.stopScreenShare()
                    } else {
                        viewModel.clearScreenShareNotice()
                        viewModel.requestScreenShareConfirmation()
                    }
                },
                onInterruptSpeaking = {
                    viewModel.liveSessionManager.interrupt()
                }
            )
        }

        // Compact Text Chat Sheet (bottom sheet on mobile, side panel on wide screens)
        AnimatedVisibility(
            visible = isChatOpen,
            enter = if (isWideLayout) {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            } else {
                slideInVertically(initialOffsetY = { it }) + fadeIn()
            },
            exit = if (isWideLayout) {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            } else {
                slideOutVertically(targetOffsetY = { it }) + fadeOut()
            },
            modifier = Modifier.align(if (isWideLayout) Alignment.CenterEnd else Alignment.BottomCenter)
        ) {
            ChatSheet(
                messages = chatMessages,
                readRepliesAloud = settings.readRepliesAloud,
                reducedMotion = settings.reducedMotion,
                isWideLayout = isWideLayout,
                maxSheetHeight = maxChatHeight,
                onToggleReadAloud = viewModel::setReadRepliesAloud,
                onSendMessage = viewModel::sendChatMessage,
                onClearChat = viewModel::clearChat,
                onClose = viewModel::closeChat
            )
        }

        // Slide-in Settings Drawer
        AnimatedVisibility(
            visible = isSettingsOpen,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            SettingsDrawer(
                assistant = assistant,
                screenShare = screenShare,
                settings = settings,
                reliability = reliability,
                onConnect = viewModel::connectOnly,
                onDisconnect = viewModel::disconnectSession,
                onSelectCharacter = viewModel::selectCharacter,
                onToggleReadAloud = viewModel::setReadRepliesAloud,
                onToggleAnimation = viewModel::setAnimationEnabled,
                onToggleReducedMotion = viewModel::setReducedMotion,
                onStartScreenShare = {
                    viewModel.closeSettings()
                    viewModel.requestScreenShareConfirmation()
                },
                onStopScreenShare = viewModel::stopScreenShare,
                onSetFrameQuality = viewModel::setScreenFrameQuality,
                onSetFrameRate = viewModel::setScreenFrameRate,
                onRunHealthCheck = viewModel::runManualHealthCheck,
                onClose = viewModel::closeSettings
            )
        }

        // Screen Share Privacy Confirmation Dialog
        if (showScreenShareDialog) {
            ScreenSharePrivacyDialog(
                onContinue = {
                    viewModel.dismissScreenShareConfirmation()
                    launchSystemScreenShareConsent()
                },
                onCancel = {
                    viewModel.dismissScreenShareConfirmation()
                }
            )
        }
    }
}
