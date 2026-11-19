package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.state.ChatMessage
import com.example.state.ChatRole
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.ElevatedNavyCard
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.InkMuted
import com.example.ui.theme.InkPrimary
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.RoyalGold

@Composable
fun ChatSheet(
    messages: List<ChatMessage>,
    readRepliesAloud: Boolean,
    reducedMotion: Boolean,
    isWideLayout: Boolean,
    maxSheetHeight: Dp,
    onToggleReadAloud: (Boolean) -> Unit,
    onSendMessage: (String) -> Unit,
    onClearChat: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val containerModifier = if (isWideLayout) {
        modifier
            .widthIn(min = 340.dp, max = 420.dp)
            .fillMaxHeight()
            .padding(top = 72.dp, end = 16.dp, bottom = 16.dp)
    } else {
        modifier
            .fillMaxWidth()
            .height(maxSheetHeight)
    }

    Surface(
        color = GlassNavySurface.copy(alpha = 0.94f),
        shape = if (isWideLayout) RoundedCornerShape(24.dp) else RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        border = BorderStroke(1.dp, GlassBorder),
        modifier = containerModifier
            .navigationBarsPadding()
            .imePadding()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.chat_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = readRepliesAloud,
                        onClick = { onToggleReadAloud(!readRepliesAloud) },
                        label = {
                            Text(
                                text = if (readRepliesAloud) "Voice On" else "Muted",
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (readRepliesAloud) Icons.AutoMirrored.Rounded.VolumeUp else Icons.AutoMirrored.Rounded.VolumeOff,
                                contentDescription = stringResource(R.string.chat_read_aloud),
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CyanPulse.copy(alpha = 0.2f),
                            selectedLabelColor = InkPrimary,
                            selectedLeadingIconColor = CyanPulse,
                            labelColor = InkSecondary
                        ),
                        modifier = Modifier.testTag("chat_read_aloud_toggle")
                    )

                    IconButton(
                        onClick = onClearChat,
                        enabled = messages.isNotEmpty(),
                        modifier = Modifier
                            .testTag("chat_clear_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteSweep,
                            contentDescription = "Clear chat",
                            tint = if (messages.isNotEmpty()) InkSecondary else InkMuted
                        )
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .testTag("chat_close_button")
                            .minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close chat",
                            tint = InkPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // In-memory messages list
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Type or talk in English or বাংলা.\nAklima AI always replies in her female voice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ChatBubble(message = msg, reducedMotion = reducedMotion)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input Row (IME & Bengali safe)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.chat_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkMuted
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            val trimmed = draft.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendMessage(trimmed)
                                draft = ""
                            }
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPulse,
                        unfocusedBorderColor = GlassBorder,
                        focusedTextColor = InkPrimary,
                        unfocusedTextColor = InkPrimary,
                        cursorColor = CyanPulse,
                        focusedContainerColor = ElevatedNavyCard.copy(alpha = 0.7f),
                        unfocusedContainerColor = ElevatedNavyCard.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input_field")
                )

                val canSend = draft.trim().isNotEmpty()
                Surface(
                    shape = CircleShape,
                    color = if (canSend) CyanPulse else ElevatedNavyCard,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_send_button")
                ) {
                    IconButton(
                        onClick = {
                            val trimmed = draft.trim()
                            if (trimmed.isNotEmpty()) {
                                onSendMessage(trimmed)
                                draft = ""
                            }
                        },
                        enabled = canSend
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send message",
                            tint = if (canSend) ObsidianVoid else InkMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    reducedMotion: Boolean
) {
    val isUser = message.role != ChatRole.ASSISTANT
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) {
                CyanPulse.copy(alpha = 0.18f)
            } else {
                ElevatedNavyCard.copy(alpha = 0.88f)
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isUser) 18.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 18.dp
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (message.isSpeakingNow) RoyalGold.copy(alpha = 0.7f) else GlassBorder
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                if (!isUser && message.isSpeakingNow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        SpeakingEqualizerBadge(reducedMotion = reducedMotion)
                        Text(
                            text = "Speaking",
                            style = MaterialTheme.typography.labelSmall,
                            color = RoyalGold
                        )
                    }
                } else if (message.role == ChatRole.USER_VOICE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = "Spoken message",
                            tint = ElectricTeal,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Voice",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricTeal
                        )
                    }
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkPrimary
                )
            }
        }
    }
}

@Composable
private fun SpeakingEqualizerBadge(reducedMotion: Boolean) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(380), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(420), RepeatMode.Reverse),
        label = "b2"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(12.dp)
    ) {
        val h1 = if (reducedMotion) 0.7f else bar1
        val h2 = if (reducedMotion) 0.9f else bar2
        val h3 = if (reducedMotion) 0.5f else bar1
        listOf(h1, h2, h3).forEach { factor ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height((12 * factor).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(RoyalGold)
            )
        }
    }
}
