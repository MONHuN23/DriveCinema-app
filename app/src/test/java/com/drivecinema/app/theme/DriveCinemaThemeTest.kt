package com.drivecinema.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.sp
import com.drivecinema.app.ui.design.DcTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Milestone P: the app's theme is DriveCinema's own dark scheme on every device. On Android 12+ (SDK 34 here) the old
 * template took the wallpaper's colours (dynamic colour), so Material's defaults didn't match the app.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveCinemaThemeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun materialDefaultsUseDriveCinemaColoursNotTheWallpaper() {
        var primary = Color.Unspecified
        var background = Color.Unspecified
        var surface = Color.Unspecified
        var onSurface = Color.Unspecified
        composeTestRule.setContent {
            DriveCinemaTheme {
                primary = MaterialTheme.colorScheme.primary
                background = MaterialTheme.colorScheme.background
                surface = MaterialTheme.colorScheme.surface
                onSurface = MaterialTheme.colorScheme.onSurface
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(Color(DcTokens.Colors.BRAND), primary)
        assertEquals(Color(DcTokens.Colors.CANVAS), background)
        assertEquals(Color(DcTokens.Colors.SURFACE_1), surface)
        assertEquals(Color(DcTokens.Colors.TEXT_1), onSurface)
    }

    /** Home's text that relies on Material's default style must not change size (Home stays as it is). */
    @Test
    fun materialTypographyStaysAtMaterialsDefaults() {
        var typography: Typography? = null
        composeTestRule.setContent { DriveCinemaTheme { typography = MaterialTheme.typography } }
        composeTestRule.waitForIdle()
        val defaults = Typography()
        assertEquals(defaults.bodyLarge.fontSize, typography!!.bodyLarge.fontSize)
        assertEquals(defaults.bodyMedium.fontSize, typography!!.bodyMedium.fontSize)
        assertEquals(defaults.titleLarge.fontSize, typography!!.titleLarge.fontSize)
        assertEquals(defaults.labelLarge.fontSize, typography!!.labelLarge.fontSize)
    }

    @Test
    fun theTvGetsTheTenFootScaleAndThePhoneTheTouchScale() {
        var phone: DcFormFactor? = null
        var tv: DcFormFactor? = null
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = false) { phone = DcTheme.formFactor }
            DriveCinemaTheme(isTv = true) { tv = DcTheme.formFactor }
        }
        composeTestRule.waitForIdle()
        assertFalse(phone!!.isTv)
        assertTrue(tv!!.isTv)
        assertEquals(DcTokens.PHONE.type.body.sizeSp.sp, phone!!.type.body.fontSize)
        assertEquals(DcTokens.TV.type.body.sizeSp.sp, tv!!.type.body.fontSize)
        assertEquals(DcTokens.TV.rowMinHeight.toFloat(), tv!!.rowMinHeight.value)
    }
}
