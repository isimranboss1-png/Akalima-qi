package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.StopScreenShare
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.state.AssistantState
import com.example.state.ConnectionState
import com.example.state.ScreenShareState
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalGold

@Composable
fun BottomControlDock(
    assistantState: AssistantState,
    connectionState: ConnectionState,
    isMicActive: Boolean,
    micAmplitude: Float,
    outputAmplitude: Float,
    errorMessage: String?,
    screenShareState: ScreenShareState,
    screenShareNotice: String?,
    hasChatMessages: Boolean,
    reducedMotion: Boolean,
    onToggleMic: () -> Unit,
    onOpenChat: () -> Unit,
    onToggleScreenShare: () -> Unit,
    onInterruptSpeaking: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stateLabel = when {
        errorMessage != null || assistantState == AssistantState.ERROR ->
            errorMessage ?: stringResource(R.string.status_error)
        connectionState == ConnectionState.CONNECTING ->
            stringResource(R.string.status_connecting)
        assistantState == AssistantState.SPEAKING ->
            stringResource(R.string.status_speaking)
        assistantState == AssistantState.THINKING ->
            stringResource(R.string.status_thinking)
        isMicActive || assistantState == AssistantState.LISTENING ->
            stringResource(R.string.status_listening)
        else ->
            stringResource(R.string.status_tap_to_talk)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WaveformView(
            assistantState = assistantState,
            micAmplitude = micAmplitude,
            outputAmplitude = outputAmplitude,
            reducedMotion = reducedMotion,
            modifier = Modifier.widthIn(max = 480.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stateLabel,
            style = MaterialTheme.typography.labelLarge,
            color = if (assistantState == AssistantState.ERROR) RoseAlert else InkPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .padding(horizontal = 12.dp)
        )

        if (!screenShareNotice.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = screenShareNotice,
                style = MaterialTheme.typography.labelMedium,
                color = CyanPulse,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Chat sheet trigger
            SecondaryDockButton(
                label = "Chat",
                icon = Icons.Outlined.ChatBubbleOutline,
                isActive = hasChatMessages,
                activeColor = CyanPulse,
                contentDesc = "Open text chat with Aklima AI",
                testTag = "chat_button",
                onClick = onOpenChat
            )

            // Center: Primary Microphone Button
            PrimaryMicButton(
                assistantState = assistantState,
                connectionState = connectionState,
                isMicActive = isMicActive,
                reducedMotion = reducedMotion,
                onClick = {
                    if (assistantState == AssistantState.SPEAKING && !isMicActive) {
                        onInterruptSpeaking()
                    } else {
                        onToggleMic()
                    }
                }
            )

            // Right: Share Screen button
            val isSharing = screenShareState == ScreenShareState.ACTIVE ||
                screenShareState == ScreenShareState.PAUSED ||
                screenShareState == ScreenShareState.STARTING
            SecondaryDockButton(
                label = if (isSharing) "Stop" else "Share",
                icon = if (isSharing) Icons.Outlined.StopScreenShare else Icons.Outlined.ScreenShare,
                isActive = isSharing,
                activeColor = if (isSharing) RoseAlert else CyanPulse,
                contentDesc = if (isSharing) "Stop Sharing screen" else "Share Screen with Aklima AI",
                testTag = "screen_share_button",
                onClick = onToggleScreenShare
            )
        }
    }
}

@Composable
private fun PrimaryMicButton(
    assistantState: AssistantState,
    connectionState: ConnectionState,
    isMicActive: Boolean,
    reducedMotion: Boolean,
    onClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val showPulse = !reducedMotion && (isMicActive || assistantState == AssistantState.SPEAKING)
    val ringColor = when {
        assistantState == AssistantState.ERROR -> RoseAlert
        assistantState == AssistantState.SPEAKING -> RoyalGold
        isMicActive -> ElectricTeal
        else -> CyanPulse
    }

    val buttonDesc = when {
        assistantState == AssistantState.ERROR -> "Connection lost. Tap to reconnect microphone."
        isMicActive -> "Microphone active. Tap to mute microphone."
        else -> "Start talking to Aklima AI"
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(94.dp)
    ) {
        if (showPulse) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(ringColor.copy(alpha = 0.22f))
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            border = BorderStroke(2.dp, ringColor.copy(alpha = 0.85f)),
            modifier = Modifier
                .size(76.dp)
                .testTag("mic_button")
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = when {
                            assistantState == AssistantState.ERROR ->
                                listOf(RoseAlert, RoseAlert.copy(alpha = 0.75f))
                            isMicActive ->
                                listOf(ElectricTeal, CyanPulse)
                            assistantState == AssistantState.SPEAKING ->
                                listOf(RoyalGold, CyanPulse)
                            else ->
                                listOf(CyanPulse, Color(0xFF2563EB))
                        }
                    )
                )
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = buttonDesc
                    stateDescription = if (isMicActive) "Pressed" else "Not pressed"
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                when {
                    connectionState == ConnectionState.CONNECTING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = ObsidianVoid,
                            strokeWidth = 2.6.dp
                        )
                    }
                    assistantState == AssistantState.ERROR -> {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = ObsidianVoid,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    isMicActive -> {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = ObsidianVoid,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    else -> {
                        Icon(
                            imageVector = Icons.Rounded.MicOff,
                            contentDescription = null,
                            tint = ObsidianVoid,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SecondaryDockButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    activeColor: Color,
    contentDesc: String,
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .testTag(testTag)
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics {
                contentDescription = contentDesc
                stateDescription = if (isActive) "Active" else "Inactive"
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(if (isActive) activeColor.copy(alpha = 0.2f) else GlassNavySurface)
                .border(
                    width = 1.dp,
                    color = if (isActive) activeColor.copy(alpha = 0.8f) else GlassBorder,
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColor else InkPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isActive) activeColor else InkSecondary
        )
    }
}
