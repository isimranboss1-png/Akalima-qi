package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val AgentsDarkColorScheme = darkColorScheme(
    primary = CyanPulse,
    onPrimary = ObsidianVoid,
    primaryContainer = ElevatedNavyCard,
    onPrimaryContainer = InkPrimary,
    secondary = ElectricTeal,
    onSecondary = ObsidianVoid,
    secondaryContainer = DeepSpaceNavy,
    onSecondaryContainer = InkPrimary,
    tertiary = RoyalGold,
    onTertiary = ObsidianVoid,
    background = ObsidianVoid,
    onBackground = InkPrimary,
    surface = DeepSpaceNavy,
    onSurface = InkPrimary,
    surfaceVariant = ElevatedNavyCard,
    onSurfaceVariant = InkSecondary,
    outline = GlassBorder,
    outlineVariant = SubtleOutline,
    error = RoseAlert,
    onError = ObsidianVoid
)

private val AgentsShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AgentsDarkColorScheme,
        typography = Typography,
        shapes = AgentsShapes,
        content = content
    )
}
