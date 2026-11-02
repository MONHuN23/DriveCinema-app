package com.drivecinema.app.ui.naming

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DriveCinemaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * UI task ui/tv-browse-naming, the bug "a selected result's poster disappears": a focused naming row drew its content
 * through Milestone P's Ink tint, which paints an opaque poster as a black box. These draw real pixels (Robolectric's
 * native graphics) and read the poster's tile back. Without a TMDB poster the tile is [DcColors.Surface2], so a tint
 * shows as [DcColors.Ink].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class NamingResultFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val show = TmdbMetadata(1, "One Tree Hill", "Two half brothers share a last name.", null, null, 2003)

    private lateinit var inputMode: InputModeManager

    /** The theme, with the input mode captured: a clickable row takes the focus only outside touch mode. */
    private fun render(content: @Composable () -> Unit) {
        rule.setContent {
            inputMode = LocalInputModeManager.current
            DriveCinemaTheme(isTv = true, content = content)
        }
    }

    private fun focusAndReadPosterColour(row: String, poster: String): Color {
        // The remote's D-pad puts the app in keyboard mode.
        rule.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        rule.onNodeWithTag(row).performSemanticsAction(SemanticsActions.RequestFocus)
        // The focus animation (150 ms) done.
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        rule.onNodeWithTag(row).assertIsFocused()
        // The window drawn into a bitmap (captureToImage waits for a frame Robolectric's paused looper never sends).
        val center = rule.onNodeWithTag(poster, useUnmergedTree = true).fetchSemanticsNode().boundsInWindow.center
        val window = rule.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread { window.draw(Canvas(bitmap)) }
        return Color(bitmap.getPixel(center.x.toInt(), center.y.toInt()))
    }

    private fun Color.isNear(other: Color) =
        abs(red - other.red) < 0.03f && abs(green - other.green) < 0.03f && abs(blue - other.blue) < 0.03f

    @Test
    fun aFocusedResultKeepsItsPoster() {
        render { ResultRow(show, animated = false, onClick = {}, modifier = Modifier.testTag("row")) }
        val colour = focusAndReadPosterColour("row", "naming_poster_1")
        assertTrue("the poster tile keeps its colour, was $colour", colour.isNear(DcColors.Surface2))
    }

    /** The cause, kept visible: the white fill's tint turns the same tile Ink. Proves the check above can fail. */
    @Test
    fun theWhiteFillPaintsThePosterInk() {
        render {
            NamingRow(
                modifier = Modifier.testTag("row"),
                onClick = {},
                style = NamingRowStyle.FILLED,
                leading = { NamingPoster(null, 44.dp, Modifier.testTag("poster")) }
            ) { NamingLine(show.title) }
        }
        val colour = focusAndReadPosterColour("row", "poster")
        assertTrue("the tint paints the tile Ink, was $colour", colour.isNear(DcColors.Ink))
    }
}
