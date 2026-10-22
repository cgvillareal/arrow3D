package com.arrow3d.game.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette from the design doc: soft dark background, three-tone faces, bright arrows. */
object GameColors {
    val Background = Color(0xFF14172B)
    val Card = Color(0xFF1E2240)
    val CardPressed = Color(0xFF272C52)
    val Face = Color(0xFF2E3369)
    val Grid = Color(0xFF5A62A8)
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFF9AA3C7)

    // Arrow states (design doc, section 3)
    val ArrowRest = Color(0xFF2DD4BF)
    val ArrowBlocked = Color(0xFFF87171)
    val ArrowExitStart = Color(0xFFFBBF24)
    val ArrowExitEnd = Color(0xFFA78BFA)
    val ArrowTraveler = Color(0xFFE879F9)

    val Heart = Color(0xFFF87171)
    val Star = Color(0xFFFBBF24)
    val Progress = Color(0xFF2DD4BF)
    val ProgressTrack = Color(0xFF2B3160)

    // Mode accents (mockup "Elegir dificultad")
    val Zen = Color(0xFF60A5FA)
    val Easy = Color(0xFF34D399)
    val Medium = Color(0xFFFBBF24)
    val Hard = Color(0xFFFB923C)
    val Expert = Color(0xFFF87171)
    val Adaptive = Color(0xFFA78BFA)
}

private val Scheme = darkColorScheme(
    primary = GameColors.ArrowRest,
    onPrimary = GameColors.Background,
    secondary = GameColors.ArrowTraveler,
    background = GameColors.Background,
    onBackground = GameColors.TextPrimary,
    surface = GameColors.Card,
    onSurface = GameColors.TextPrimary,
    surfaceVariant = GameColors.Card,
    onSurfaceVariant = GameColors.TextSecondary,
)

@Composable
fun Arrow3dTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
