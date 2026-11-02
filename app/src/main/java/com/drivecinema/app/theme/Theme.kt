package com.drivecinema.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.drivecinema.app.ui.design.DcTokens

/**
 * Milestone P: DriveCinema is dark-only, with its own colours. The template's purple scheme and Android 12's
 * wallpaper colours (dynamic colour) are gone, so Material's defaults (text fields, chips, ripples) match the app.
 */
internal val DriveCinemaColorScheme = darkColorScheme(
    primary = DcColors.Brand,
    onPrimary = DcColors.OnBrand,
    primaryContainer = DcColors.BrandContainer,
    onPrimaryContainer = DcColors.Text1,
    inversePrimary = DcColors.Brand,
    secondary = DcColors.Surface3,
    onSecondary = DcColors.Text1,
    secondaryContainer = DcColors.Surface3,
    onSecondaryContainer = DcColors.Text1,
    tertiary = DcColors.Success,
    onTertiary = DcColors.Ink,
    tertiaryContainer = DcColors.Surface2,
    onTertiaryContainer = DcColors.Success,
    background = DcColors.Canvas,
    onBackground = DcColors.Text1,
    surface = DcColors.Surface1,
    onSurface = DcColors.Text1,
    surfaceVariant = DcColors.Surface2,
    onSurfaceVariant = DcColors.Text2,
    surfaceTint = Color.Transparent,
    inverseSurface = DcColors.Focus,
    inverseOnSurface = DcColors.Ink,
    error = DcColors.Danger,
    onError = DcColors.Ink,
    errorContainer = DcColors.DangerContainer,
    onErrorContainer = DcColors.Danger,
    outline = DcColors.Outline,
    outlineVariant = DcColors.Divider,
    scrim = Color.Black,
    surfaceBright = DcColors.Surface3,
    surfaceDim = DcColors.Canvas,
    surfaceContainerLowest = DcColors.Canvas,
    surfaceContainerLow = DcColors.Surface1,
    surfaceContainer = DcColors.Surface1,
    surfaceContainerHigh = DcColors.Surface2,
    surfaceContainerHighest = DcColors.Surface3,
)

/** What differs between the phone and the TV: type scale and control sizes ([DcTokens.FormFactor]). */
@Immutable
data class DcFormFactor(
    val isTv: Boolean,
    val type: DcType,
    val buttonHeight: Dp,
    val smallButtonHeight: Dp,
    val rowMinHeight: Dp,
    val iconSize: Dp,
    val sideMargin: Dp
) {
    companion object {
        val Phone = from(DcTokens.PHONE, DcType.Phone, isTv = false)
        val Tv = from(DcTokens.TV, DcType.Tv, isTv = true)

        private fun from(tokens: DcTokens.FormFactor, type: DcType, isTv: Boolean) = DcFormFactor(
            isTv = isTv,
            type = type,
            buttonHeight = tokens.buttonHeight.dp,
            smallButtonHeight = tokens.smallButtonHeight.dp,
            rowMinHeight = tokens.rowMinHeight.dp,
            iconSize = tokens.iconSize.dp,
            sideMargin = tokens.sideMargin.dp
        )
    }
}

val LocalDcFormFactor = staticCompositionLocalOf { DcFormFactor.Phone }

/** The design's values for this device: `DcTheme.type.title`, `DcTheme.formFactor.rowMinHeight`, … */
object DcTheme {
    val formFactor: DcFormFactor
        @Composable @ReadOnlyComposable get() = LocalDcFormFactor.current

    val type: DcType
        @Composable @ReadOnlyComposable get() = LocalDcFormFactor.current.type
}

@Composable
fun DriveCinemaTheme(
    isTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = DriveCinemaColorScheme, typography = Typography) {
        CompositionLocalProvider(LocalDcFormFactor provides if (isTv) DcFormFactor.Tv else DcFormFactor.Phone, content = content)
    }
}
