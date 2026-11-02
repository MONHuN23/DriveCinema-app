package com.drivecinema.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.app.ui.design.DcTokens.Colors as T

/** The same dark cinema palette as the Android app: both read the shared [DcTokens] (Milestone P). */
object DesktopColors {
    val Background = Color(T.CANVAS)
    val Surface = Color(T.SURFACE_1)
    val SurfaceRaised = Color(T.SURFACE_2)
    val Accent = Color(T.BRAND)
    /** Focus highlight of the library's cards, as before. New controls use [FocusRing]. */
    val Focus = Color(T.CARD_FOCUS_BLUE)
    val TextPrimary = Color(T.TEXT_1)
    val TextSecondary = Color(T.TEXT_2)

    // Milestone P
    val SurfaceSelected = Color(T.SURFACE_3)
    val Divider = Color(T.DIVIDER)
    val Outline = Color(T.OUTLINE)
    val TextTertiary = Color(T.TEXT_3)
    val OnAccent = Color(T.ON_BRAND)
    val AccentContainer = Color(T.BRAND_CONTAINER)
    val FocusRing = Color(T.FOCUS)
    val Success = Color(T.SUCCESS)
    val Warning = Color(T.WARNING)
    val WarningContainer = Color(T.WARNING_CONTAINER)
    val Danger = Color(T.DANGER)
    val DangerContainer = Color(T.DANGER_CONTAINER)
}

/** Milestone P: the desktop's type roles (design §3.2), from [DcTokens.DESKTOP]. */
object DesktopType {
    private val scale = DcTokens.DESKTOP.type
    val display = scale.display.toTextStyle()
    val headline = scale.headline.toTextStyle()
    val title = scale.title.toTextStyle()
    val rowTitle = scale.rowTitle.toTextStyle()
    val body = scale.body.toTextStyle()
    val label = scale.label.toTextStyle()
    val caption = scale.caption.toTextStyle()

    private fun DcTokens.TextStyleSpec.toTextStyle() =
        TextStyle(fontSize = sizeSp.sp, lineHeight = lineHeightSp.sp, fontWeight = FontWeight(weight))
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
