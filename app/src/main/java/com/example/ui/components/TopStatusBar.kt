package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.state.ConnectionState
import com.example.state.ScreenShareState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary

@Composable
fun TopStatusBar(
    connectionState: ConnectionState,
    screenShareState: ScreenShareState,
    onStopScreenShare: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Brand & Online Indicator
        Surface(
            color = GlassNavySurface,
            shape = RoundedCornerShape(999.dp),
            border = BorderStroke(1.dp, GlassBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(10.dp))
                val dotColor = when (connectionState) {
                    ConnectionState.CONNECTED -> EmeraldOnline
                    ConnectionState.CONNECTING -> AmberWarning
                    ConnectionState.DISCONNECTED -> InkMuted
                }
                val statusLabel = when (connectionState) {
                    ConnectionState.CONNECTED -> stringResource(R.string.status_online)
                    ConnectionState.CONNECTING -> stringResource(R.string.status_connecting)
                    ConnectionState.DISCONNECTED -> stringResource(R.string.status_offline)
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Persistent Screen Sharing indicator pill
            val isSharingVisible = screenShareState == ScreenShareState.ACTIVE ||
                screenShareState == ScreenShareState.PAUSED ||
                screenShareState == ScreenShareState.STARTING

            AnimatedVisibility(
                visible = isSharingVisible,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = CyanPulse.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.7f)),
                    modifier = Modifier
                        .testTag("screen_share_indicator")
                        .minimumInteractiveComponentSize()
                        .clickable { onStopScreenShare() }
                        .semantics {
                            contentDescription = "Screen Sharing active. Tap to stop sharing."
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(EmeraldOnline)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Outlined.ScreenShare,
                            contentDescription = null,
                            tint = CyanPulse,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.screen_share_active),
                            style = MaterialTheme.typography.labelMedium,
                            color = InkPrimary,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Stop Sharing",
                            tint = InkSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // Settings Button
            Surface(
                color = GlassNavySurface,
                shape = CircleShape,
                border = BorderStroke(1.dp, GlassBorder)
            ) {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .testTag("settings_button")
                        .minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Open Settings",
                        tint = InkPrimary
                    )
                }
            }
        }
    }
}
