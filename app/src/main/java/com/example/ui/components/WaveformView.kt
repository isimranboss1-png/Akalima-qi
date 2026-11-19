package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.state.AssistantState
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.InkMuted
import com.example.ui.theme.RoseAlert
import com.example.ui.theme.RoyalGold
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun WaveformView(
    assistantState: AssistantState,
    micAmplitude: Float,
    outputAmplitude: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val rawLevel = when (assistantState) {
        AssistantState.LISTENING -> micAmplitude
        AssistantState.SPEAKING -> outputAmplitude
        AssistantState.THINKING -> 0.25f
        AssistantState.IDLE, AssistantState.ERROR -> 0.04f
    }

    val smoothedLevel by animateFloatAsState(
        targetValue = rawLevel,
        animationSpec = tween(durationMillis = 90),
        label = "waveform_level"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "waveform_phase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (assistantState == AssistantState.THINKING) 2200 else 1100,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
    ) {
        val barCount = 29
        val barWidth = 4.dp.toPx()
        val totalSpacing = size.width - (barCount * barWidth)
        val stepX = barWidth + (totalSpacing / (barCount - 1).coerceAtLeast(1))
        val centerY = size.height / 2f
        val maxHalfHeight = size.height * 0.45f
        val minBarHeight = 4.dp.toPx()

        val brush = when (assistantState) {
            AssistantState.SPEAKING -> Brush.verticalGradient(listOf(RoyalGold, CyanPulse))
            AssistantState.LISTENING -> Brush.verticalGradient(listOf(ElectricTeal, CyanPulse))
            AssistantState.THINKING -> Brush.verticalGradient(listOf(CyanPulse.copy(alpha = 0.85f), ElectricTeal.copy(alpha = 0.5f)))
            AssistantState.ERROR -> Brush.verticalGradient(listOf(RoseAlert.copy(alpha = 0.7f), RoseAlert.copy(alpha = 0.4f)))
            AssistantState.IDLE -> Brush.verticalGradient(listOf(InkMuted.copy(alpha = 0.55f), InkMuted.copy(alpha = 0.3f)))
        }

        for (i in 0 until barCount) {
            val centerFactor = 1f - abs((i - barCount / 2f) / (barCount / 2f)) * 0.65f
            val wave = if (reducedMotion) {
                0.5f
            } else {
                when (assistantState) {
                    AssistantState.LISTENING, AssistantState.SPEAKING -> {
                        val s1 = sin(phase * 2.2f + i * 0.55f) * 0.5f + 0.5f
                        val s2 = sin(-phase * 1.4f + i * 0.9f) * 0.5f + 0.5f
                        (s1 * 0.6f + s2 * 0.4f)
                    }
                    AssistantState.THINKING -> {
                        sin(phase + i * 0.35f) * 0.5f + 0.5f
                    }
                    AssistantState.IDLE, AssistantState.ERROR -> 0.15f
                }
            }

            val energy = when (assistantState) {
                AssistantState.LISTENING, AssistantState.SPEAKING ->
                    (0.14f + smoothedLevel * 0.86f) * wave * centerFactor
                AssistantState.THINKING ->
                    (0.18f + wave * 0.34f) * centerFactor
                AssistantState.IDLE, AssistantState.ERROR ->
                    0.08f * centerFactor
            }

            val barH = maxOf(minBarHeight, maxHalfHeight * 2f * energy)
            val left = i * stepX
            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, centerY - barH / 2f),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
