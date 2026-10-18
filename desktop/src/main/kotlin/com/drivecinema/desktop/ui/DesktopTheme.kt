package com.drivecinema.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The same dark cinema palette as the Android app. */
object DesktopColors {
    val Background = Color(0xFF0C0D10)
    val Surface = Color(0xFF16181D)
    val SurfaceRaised = Color(0xFF1E2128)
    val Accent = Color(0xFFE50914)
    val Focus = Color(0xFF4D88FF)
    val TextPrimary = Color(0xFFF0F2F5)
    val TextSecondary = Color(0xFFB0B3B8)
}

private val DriveCinemaDarkColors = darkColorScheme(
    primary = DesktopColors.Accent,
    onPrimary = Color.White,
    secondary = DesktopColors.Focus,
    background = DesktopColors.Background,
    onBackground = DesktopColors.TextPrimary,
    surface = DesktopColors.Surface,
    onSurface = DesktopColors.TextPrimary,
    surfaceVariant = DesktopColors.SurfaceRaised,
    onSurfaceVariant = DesktopColors.TextSecondary
)

@Composable
fun DriveCinemaDesktopTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DriveCinemaDarkColors, content = content)
}
