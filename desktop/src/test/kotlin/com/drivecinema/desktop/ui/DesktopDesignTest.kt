package com.drivecinema.desktop.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.design.DcButtonKind
import com.drivecinema.app.ui.design.DcStatusKind
import com.drivecinema.app.ui.design.DcTokens
import com.drivecinema.desktop.ui.design.desktopBannerColors
import com.drivecinema.desktop.ui.design.desktopButtonColors
import com.drivecinema.desktop.ui.design.desktopStatusColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** Milestone P (Task P.1): the desktop reads the same design tokens as the phone and TV. */
class DesktopDesignTest {

    /** The values the desktop had before the tokens: the library and the menu look the same. */
    @Test
    fun theDesktopPaletteIsTheSharedTokensAndUnchanged() {
        assertEquals(Color(0xFF0C0D10), DesktopColors.Background)
        assertEquals(Color(0xFF16181D), DesktopColors.Surface)
        assertEquals(Color(0xFF1E2128), DesktopColors.SurfaceRaised)
        assertEquals(Color(0xFFE50914), DesktopColors.Accent)
        assertEquals(Color(0xFF4D88FF), DesktopColors.Focus)
        assertEquals(Color(0xFFF0F2F5), DesktopColors.TextPrimary)
        assertEquals(Color(0xFFB0B3B8), DesktopColors.TextSecondary)
        assertEquals(Color(DcTokens.Colors.CANVAS), DesktopColors.Background)
        assertEquals(Color(DcTokens.Colors.DANGER), DesktopColors.Danger)
        assertEquals(Color(DcTokens.Colors.FOCUS), DesktopColors.FocusRing)
    }

    @Test
    fun hoverLiftsAButtonOneStepAndDisabledDimsIt() {
        assertEquals(DesktopColors.SurfaceRaised, desktopButtonColors(DcButtonKind.Secondary, hovered = false).container)
        assertEquals(DesktopColors.SurfaceSelected, desktopButtonColors(DcButtonKind.Secondary, hovered = true).container)
        assertEquals(Color.Transparent, desktopButtonColors(DcButtonKind.Tertiary, hovered = false).container)
        assertEquals(DesktopColors.TextPrimary, desktopButtonColors(DcButtonKind.Tertiary, hovered = true).content)
        assertNotEquals(DesktopColors.Accent, desktopButtonColors(DcButtonKind.Primary, hovered = true).container)
        assertEquals(DesktopColors.OnAccent, desktopButtonColors(DcButtonKind.Primary, hovered = true).content)
        assertEquals(DesktopColors.Danger, desktopButtonColors(DcButtonKind.Destructive, hovered = false).content)

        val disabled = desktopButtonColors(DcButtonKind.Primary, hovered = true, enabled = false)
        assertEquals(DesktopColors.Surface, disabled.container)
        assertEquals(DcTokens.DISABLED_ALPHA, disabled.content.alpha, 0.01f)
    }

    /** The Windows firewall tip was shown in brand red and read like an error (audit, `docs/audit-history.md` §58). */
    @Test
    fun informationIsNeverBrandRed() {
        DcBannerKind.entries.forEach {
            assertNotEquals(DesktopColors.Accent, desktopBannerColors(it).container)
            assertNotEquals(DesktopColors.Accent, desktopBannerColors(it).content)
        }
        assertEquals(DesktopColors.SurfaceRaised, desktopBannerColors(DcBannerKind.Info).container)
        assertEquals(DesktopColors.Success, desktopStatusColors(DcStatusKind.Success).content)
    }

    @Test
    fun theDesktopTypeScaleComesFromTheTokens() {
        assertEquals(DcTokens.DESKTOP.type.headline.sizeSp.sp, DesktopType.headline.fontSize)
        assertEquals(DcTokens.DESKTOP.type.body.sizeSp.sp, DesktopType.body.fontSize)
        assertEquals(14.sp, DesktopType.label.fontSize)
    }
}
