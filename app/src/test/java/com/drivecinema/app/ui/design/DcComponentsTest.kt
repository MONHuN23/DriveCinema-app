package com.drivecinema.app.ui.design

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.unit.dp
import com.drivecinema.app.theme.DcColors
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.icons.AppIcons
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Milestone P (Task P.1): the phone/TV components behave the same on a tap and on the TV remote. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DcComponentsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** The remote can't land on it: no focus action in its semantics. */
    private val notFocusable = SemanticsMatcher.keyNotDefined(SemanticsActions.RequestFocus)

    private fun okKey(action: Int) =
        androidx.compose.ui.input.key.KeyEvent(NativeKeyEvent(action, KeyEvent.KEYCODE_DPAD_CENTER))

    private fun SemanticsNodeInteraction.pressOk() {
        performKeyPress(okKey(KeyEvent.ACTION_DOWN))
        performKeyPress(okKey(KeyEvent.ACTION_UP))
    }

    private fun setTvContent(content: @Composable () -> Unit) {
        composeTestRule.setContent { DriveCinemaTheme(isTv = true) { Column { content() } } }
    }

    @Test
    fun oneOkPressOnAFocusedButtonIsOneClick() {
        var clicks = 0
        setTvContent { DcButton("Refresh", onClick = { clicks++ }, modifier = Modifier.testTag("button")) }
        val button = composeTestRule.onNodeWithTag("button")
        button.requestFocus()
        button.assertIsFocused()
        button.pressOk()
        assertEquals(1, clicks)
        button.performClick()
        assertEquals(2, clicks)
    }

    @Test
    fun aDisabledButtonCanNotBeFocusedOrClicked() {
        var clicks = 0
        setTvContent {
            DcButton("Pair", onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("button"))
        }
        val button = composeTestRule.onNodeWithTag("button")
        button.performClick()
        button.assert(notFocusable)
        assertEquals(0, clicks)
    }

    /** A loading button keeps the remote's focus (it doesn't vanish from under it, the F-32 lesson) but ignores OK. */
    @Test
    fun aLoadingButtonKeepsFocusButIgnoresPresses() {
        var clicks = 0
        setTvContent {
            DcButton("Pairing…", onClick = { clicks++ }, loading = true, modifier = Modifier.testTag("button"))
        }
        val button = composeTestRule.onNodeWithTag("button")
        button.requestFocus()
        button.assertIsFocused()
        button.pressOk()
        button.performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun aSwitchRowFlipsOncePerTapAndOncePerOkPress() {
        var checked by mutableStateOf(true)
        setTvContent {
            DcSwitchRow(
                title = "Skip intro",
                checked = checked,
                onCheckedChange = { checked = it },
                modifier = Modifier.testTag("switch")
            )
        }
        val switch = composeTestRule.onNodeWithTag("switch")
        switch.assertIsOn()
        switch.performClick()
        assertFalse(checked)
        switch.assertIsOff()

        switch.requestFocus()
        switch.pressOk()
        assertTrue(checked)
        switch.assertIsOn()
    }

    @Test
    fun aRowWithoutAnActionIsNotFocusableAndARowWithOneClicksOnce() {
        var clicks = 0
        setTvContent {
            DcListRow("Google Drive connected", modifier = Modifier.testTag("info"))
            DcListRow("My PCs", icon = AppIcons.DesktopWindows, onClick = { clicks++ }, modifier = Modifier.testTag("row"))
        }
        composeTestRule.onNodeWithTag("info").assert(notFocusable)
        val row = composeTestRule.onNodeWithTag("row")
        row.requestFocus()
        row.assertIsFocused()
        row.pressOk()
        assertEquals(1, clicks)
    }

    @Test
    fun theTvGetsTallerRowsThanThePhone() {
        composeTestRule.setContent {
            Column {
                DriveCinemaTheme(isTv = true) { DcListRow("TV row", onClick = {}, modifier = Modifier.testTag("tv")) }
                DriveCinemaTheme(isTv = false) { DcButton("Phone", onClick = {}, modifier = Modifier.testTag("phone")) }
            }
        }
        composeTestRule.onNodeWithTag("tv").assertHeightIsAtLeast(DcTokens.TV.rowMinHeight.dp)
        composeTestRule.onNodeWithTag("phone").assertHeightIsAtLeast(DcTokens.PHONE.buttonHeight.dp)
    }

    /**
     * The focus look is drawn, not composed: moving focus onto a button recomposes nothing. A control that reads its
     * focus in composition (as the old buttons do) is the check that the counter works.
     */
    @Test
    fun movingFocusOntoAButtonRecomposesNothing() {
        setTvContent {
            DcButton("Play", onClick = {}, modifier = Modifier.testTag("button"))
            DcListRow("Refresh library", onClick = {}, modifier = Modifier.testTag("row"))
            FocusReadingBox(Modifier.testTag("old"))
        }
        composeTestRule.waitForIdle()

        val beforeButton = changeCount()
        composeTestRule.onNodeWithTag("button").requestFocus()
        composeTestRule.onNodeWithTag("row").requestFocus()
        composeTestRule.waitForIdle()
        assertEquals("focus moves between Dc controls recomposed", beforeButton, changeCount())

        composeTestRule.onNodeWithTag("old").requestFocus()
        composeTestRule.waitForIdle()
        assertNotEquals("the counter didn't see a focus-reading control recompose", beforeButton, changeCount())
    }

    @Test
    fun focusedNeutralControlsTurnWhiteAndAPrimaryKeepsItsRed() {
        val primary = dcButtonColors(DcButtonKind.Primary)
        assertEquals(DcColors.Brand, primary.focusedContainer)
        assertEquals(DcColors.OnBrand, primary.focusedContent)
        val secondary = dcButtonColors(DcButtonKind.Secondary)
        assertEquals(DcColors.Focus, secondary.focusedContainer)
        assertEquals(DcColors.Ink, secondary.focusedContent)
        // A focused destructive action stays red (on white), so it never looks like any other button.
        assertEquals(DcColors.Brand, dcButtonColors(DcButtonKind.Destructive).focusedContent)
        assertEquals(DcColors.Danger, dcRowColors(destructive = true).content)
        assertEquals(DcColors.Ink, dcRowColors().focusedContent)
        assertEquals(Color.Transparent, dcDisabledContainer(DcButtonKind.Tertiary))
    }

    @Test
    fun informationIsNeverShownInBrandRed() {
        DcBannerKind.entries.forEach { kind ->
            val (container, icon) = dcBannerColors(kind)
            assertNotEquals(DcColors.Brand, container)
            assertNotEquals(DcColors.Brand, icon)
        }
        assertEquals(DcColors.Surface2, dcBannerColors(DcBannerKind.Info).first)
        assertEquals(DcColors.Success, dcStatusColors(DcStatusKind.Success).second)
    }

    private fun changeCount(): Long = composeTestRule.runOnIdle {
        Recomposer.runningRecomposers.value.sumOf { it.changeCount }
    }

    @Composable
    private fun FocusReadingBox(modifier: Modifier) {
        val interactionSource = remember { MutableInteractionSource() }
        val focused by interactionSource.collectIsFocusedAsState()
        Box(
            modifier
                .size(40.dp)
                .background(if (focused) Color.White else Color.Gray)
                .focusable(interactionSource = interactionSource)
        )
    }
}
