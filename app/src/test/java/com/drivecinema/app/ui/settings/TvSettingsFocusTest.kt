package com.drivecinema.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.mobile.home.AccountUiState
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.isFocused
import com.drivecinema.app.ui.design.assertFocusFitsVertically
import com.drivecinema.app.ui.design.DcTokens
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task P.2: the TV's two-pane Settings and the remote (decision D-P4): the first category starts focused, moving
 * through the categories shows their rows, Right enters the rows and Left or Back returns to the category.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvSettingsFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private var backs = 0
    private var signOuts = 0

    private val actions = SettingsActions(
        refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = {}, addLocalFolder = {},
        removeLocalFolder = {}, openPcs = {}, pairTv = {}, setSkipIntro = {}, openHelp = {}, openAbout = {}, onOpen = {},
        signOut = { signOuts++ }, back = { backs++ }
    )

    private fun show(
        state: SettingsScreenState = SettingsScreenState(
            account = AccountUiState(displayName = "Jani", email = "jani@example.com", isConnected = true),
            pendingDuplicateCount = 1
        )
    ) {
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvSettingsScreen(state, actions)
            }
        }
        rule.waitForIdle()
    }

    private fun move(direction: FocusDirection) {
        rule.runOnUiThread { focusManager.moveFocus(direction) }
        rule.waitForIdle()
    }

    private fun pressBack() {
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
    }

    private fun category(name: String): SemanticsNodeInteraction = rule.onNodeWithTag("tv_settings_category_$name")

    private fun row(key: String): SemanticsNodeInteraction = rule.onNodeWithTag("tv_settings_row_$key")

    @Test
    fun theFirstCategoryStartsFocusedWithItsRowsShown() {
        show()

        category("Library").assertIsFocused()
        row("refresh").assertExists()
        row("switch").assertExists()
        row("duplicates").assertExists()
    }

    @Test
    fun movingThroughTheCategoriesShowsEachOnesRows() {
        show()

        move(FocusDirection.Down)
        category("Sources").assertIsFocused()
        row("pcs").assertExists()
        row("refresh").assertDoesNotExist()

        move(FocusDirection.Down)
        category("Playback").assertIsFocused()
        row("skipIntro").assertExists()
    }

    @Test
    fun rightEntersTheRowsAndLeftReturnsToTheSameCategory() {
        show()
        move(FocusDirection.Down)    // Sources

        move(FocusDirection.Right)
        row("local").assertIsFocused()
        move(FocusDirection.Down)
        row("pcs").assertIsFocused()

        move(FocusDirection.Left)
        category("Sources").assertIsFocused()
    }

    @Test
    fun theRemoteStaysInTheRowsAtTheirEnds() {
        show()
        move(FocusDirection.Right)
        row("refresh").assertIsFocused()

        move(FocusDirection.Up)
        row("refresh").assertIsFocused()
        move(FocusDirection.Down)
        move(FocusDirection.Down)
        row("duplicates").assertIsFocused()
        move(FocusDirection.Down)
        row("duplicates").assertIsFocused()
    }

    @Test
    fun backFromTheRowsReturnsToTheCategoryNotHome() {
        show()
        move(FocusDirection.Right)
        row("refresh").assertIsFocused()

        pressBack()

        category("Library").assertIsFocused()
        assertEquals(0, backs)
    }

    @Test
    fun signOutAsksFirstAndTheRemoteStartsOnCancel() {
        show()
        category("Account").requestFocus()
        rule.waitForIdle()
        move(FocusDirection.Right)
        row("signOut").assertIsFocused()

        row("signOut").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Sign out of DriveCinema?").assertExists()
        rule.onNode(hasText("Cancel") and hasClickAction()).assertIsFocused()
        assertEquals(0, signOuts)
    }

    @Test
    fun focusedRowsAtTheEndsOfThePaneAreNotCutOff() {
        // P.7 (device check 2026-10-29): the scroll clipped "Switch library folder" below, "Skip intro" above and
        // below, "Help Center" and "Sign out" above.
        show()
        for (name in listOf("Library", "Sources", "Playback", "HelpAndAbout", "Account")) {
            category(name).requestFocus()
            rule.waitForIdle()
            move(FocusDirection.Right)
            repeat(5) {
                rule.onNode(isFocused()).assertFocusFitsVertically(
                    rule.onNodeWithTag("tv_settings_rows"), DcTokens.Focus.ROW_SCALE, "$name row ${it + 1}"
                )
                move(FocusDirection.Down)
            }
        }
    }
}
