package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ScreenShare
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.config.AppConstants
import com.example.config.CharacterRegistry
import com.example.reliability.HealthSeverity
import com.example.reliability.ReliabilitySnapshot
import com.example.state.AssistantUiSnapshot
import com.example.state.ConnectionState
import com.example.state.FrameQuality
import com.example.state.FrameRateMode
import com.example.state.ScreenShareSnapshot
import com.example.state.ScreenShareState
import com.example.state.SettingsState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.DeepSpaceNavy
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalGold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsDrawer(
    assistant: AssistantUiSnapshot,
    screenShare: ScreenShareSnapshot,
    settings: SettingsState,
    reliability: ReliabilitySnapshot,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSelectCharacter: (String) -> Unit,
    onToggleReadAloud: (Boolean) -> Unit,
    onToggleAnimation: (Boolean) -> Unit,
    onToggleReducedMotion: (Boolean) -> Unit,
    onStartScreenShare: () -> Unit,
    onStopScreenShare: () -> Unit,
    onSetFrameQuality: (FrameQuality) -> Unit,
    onSetFrameRate: (FrameRateMode) -> Unit,
    onRunHealthCheck: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianVoid.copy(alpha = 0.65f))
                .clickable(onClick = onClose)
        )

        // Slide-in Drawer Panel
        Surface(
            color = DeepSpaceNavy.copy(alpha = 0.98f),
            border = BorderStroke(1.dp, GlassBorder),
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(max = 380.dp)
                .fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineSmall,
                        color = InkPrimary
                    )
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag("settings_close_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close Settings",
                            tint = InkPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Assistant Section
                    DrawerSectionCard(title = "Assistant", icon = Icons.Rounded.RecordVoiceOver) {
                        val selectedCharacter = CharacterRegistry.getById(settings.selectedCharacterId)
                        Text(
                            text = "Character",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CharacterRegistry.all.forEach { profile ->
                                FilterChip(
                                    selected = profile.id == selectedCharacter.id,
                                    onClick = { onSelectCharacter(profile.id) },
                                    label = { Text(profile.displayName) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CyanPulse.copy(alpha = 0.22f),
                                        selectedLabelColor = InkPrimary
                                    )
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        InfoRow("Persona", "${selectedCharacter.displayName} · Warm, intelligent female companion")
                        InfoRow("Active Voice", "${assistant.activeVoiceName} (Female · Locked)", highlight = RoyalGold)
                        InfoRow("Connection", assistant.connectionState.name)

                        Spacer(modifier = Modifier.height(10.dp))
                        if (assistant.connectionState == ConnectionState.CONNECTED) {
                            OutlinedButton(
                                onClick = onDisconnect,
                                border = BorderStroke(1.dp, RoseAlert.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_disconnect_button")
                            ) {
                                Text("Disconnect Session", color = RoseAlert)
                            }
                        } else {
                            Button(
                                onClick = onConnect,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CyanPulse,
                                    contentColor = ObsidianVoid
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("settings_connect_button")
                            ) {
                                Text("Connect to Aklima AI")
                            }
                        }
                    }

                    // 2. Audio Section
                    DrawerSectionCard(title = "Audio", icon = Icons.Outlined.Mic) {
                        InfoRow(
                            "Microphone Permission",
                            if (assistant.micPermissionGranted) "Granted" else "Not granted"
                        )
                        InfoRow("Input Stream", "16,000 Hz · PCM16 Mono (${if (assistant.isMicActive) "Active" else "Standby"})")
                        InfoRow("Output Stream", "24,000 Hz · PCM16 Mono")

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Audio Level", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        val level = maxOf(assistant.micAmplitude, assistant.outputAmplitude)
                        LinearProgressIndicator(
                            progress = { level },
                            color = if (assistant.outputAmplitude > 0.05f) RoyalGold else ElectricTeal,
                            trackColor = ObsidianVoid,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        ToggleRow(
                            title = stringResource(R.string.chat_read_aloud),
                            subtitle = "Speak chat replies in Aklima AI's female voice",
                            checked = settings.readRepliesAloud,
                            onCheckedChange = onToggleReadAloud
                        )
                    }

                    // 3. Visual Section
                    DrawerSectionCard(title = "Visual", icon = Icons.Outlined.Visibility) {
                        ToggleRow(
                            title = "Character animation",
                            subtitle = "Play looping idle and speaking character motion",
                            checked = settings.animationEnabled,
                            onCheckedChange = onToggleAnimation
                        )
                        HorizontalDivider(color = GlassBorder.copy(alpha = 0.35f), modifier = Modifier.padding(vertical = 6.dp))
                        ToggleRow(
                            title = "Reduced motion",
                            subtitle = "Minimize pulsing glow and waveform animations",
                            checked = settings.reducedMotion,
                            onCheckedChange = onToggleReducedMotion
                        )
                    }

                    // 4. Screen Share Section
                    DrawerSectionCard(title = "Screen Share", icon = Icons.Outlined.ScreenShare) {
                        val isSharing = screenShare.state == ScreenShareState.ACTIVE ||
                            screenShare.state == ScreenShareState.PAUSED ||
                            screenShare.state == ScreenShareState.STARTING
                        InfoRow("Status", screenShare.state.name)
                        if (screenShare.framesSentCount > 0) {
                            InfoRow("Frames Analyzed", "${screenShare.framesSentCount}")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { if (isSharing) onStopScreenShare() else onStartScreenShare() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSharing) RoseAlert else CyanPulse,
                                contentColor = ObsidianVoid
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (isSharing) stringResource(R.string.screen_share_stop) else stringResource(R.string.screen_share_label))
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Frame Quality", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FrameQuality.entries.forEach { q ->
                                FilterChip(
                                    selected = settings.screenFrameQuality == q,
                                    onClick = { onSetFrameQuality(q) },
                                    label = { Text(q.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Frame Rate", style = MaterialTheme.typography.labelLarge, color = InkSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FrameRateMode.entries.forEach { r ->
                                FilterChip(
                                    selected = settings.screenFrameRate == r,
                                    onClick = { onSetFrameRate(r) },
                                    label = { Text(r.label) }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Screen sharing is only active after you approve the screen capture permission.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    }

                    // 5. System Health & Self-Healing Section
                    DrawerSectionCard(title = "System Health & Self-Healing", icon = Icons.Outlined.HealthAndSafety) {
                        val overallColor = when (reliability.overallSeverity) {
                            HealthSeverity.OK -> EmeraldOnline
                            HealthSeverity.WARNING -> AmberWarning
                            HealthSeverity.CRITICAL -> RoseAlert
                        }
                        InfoRow("Overall Status", reliability.overallSeverity.name, highlight = overallColor)
                        InfoRow("Last Check", formatTime(reliability.lastCheckAtMs))
                        InfoRow("Next Check", formatTime(reliability.nextCheckAtMs))
                        InfoRow(
                            "Counters",
                            "Incidents ${reliability.incidentsCount} · Repairs ${reliability.repairsCount} · Rollbacks ${reliability.rollbacksCount}"
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        reliability.subsystems.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        imageVector = if (sub.healthy) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = if (sub.healthy) EmeraldOnline else AmberWarning,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(sub.name, style = MaterialTheme.typography.bodyMedium, color = InkPrimary)
                                }
                                Text(sub.detail, style = MaterialTheme.typography.labelSmall, color = InkSecondary)
                            }
                        }

                        if (!reliability.latestDiagnosis.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = ObsidianVoid.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, CyanPulse.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "Gemini Diagnostics: ${reliability.latestDiagnosis}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CyanPulse,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onRunHealthCheck,
                            border = BorderStroke(1.dp, GlassBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Run Deep Inspection", color = InkPrimary)
                        }

                        if (reliability.auditLog.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Recent Audit Log", style = MaterialTheme.typography.labelMedium, color = InkSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            reliability.auditLog.take(6).forEach { entry ->
                                Text(
                                    text = "[${entry.formattedTime()}] ${entry.title}: ${entry.detail}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = InkMuted,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    // 6. About Section
                    DrawerSectionCard(title = "About", icon = Icons.Outlined.Info) {
                        InfoRow("Application", AppConstants.APP_NAME)
                        InfoRow("Version", AppConstants.APP_VERSION)
                        InfoRow("Model", AppConstants.LIVE_MODEL_NAME)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.about_tagline),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Surface(
        color = ElevatedNavyCard.copy(alpha = 0.85f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, GlassBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = CyanPulse, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Color = InkPrimary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
        Spacer(modifier = Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.labelLarge, color = highlight)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = InkPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianVoid,
                checkedTrackColor = CyanPulse
            )
        )
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0L) return "Pending"
    return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(ms))
}
