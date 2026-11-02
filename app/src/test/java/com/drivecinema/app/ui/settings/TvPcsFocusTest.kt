package com.drivecinema.app.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.core.host.DiscoveredHost
import com.drivecinema.app.theme.DriveCinemaTheme
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
 * Task P.3: the TV's two-pane My PCs and the remote (decision D-P4): the list starts focused, moving through it shows
 * each PC or the add steps, Right enters the pane, Left or Back returns to the same item, and the add steps take one
 * focus stop each, in step order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvPcsFocusTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var focusManager: FocusManager
    private var state by mutableStateOf(PcsScreenState())
    private var backs = 0
    private val removed = mutableListOf<String>()

    private val actions = PcsActions(
        search = {},
        pair = { _, _, _ -> },
        setVpnAddress = { _, _ -> },
        remove = { hostId ->
            removed += hostId
            state = state.copy(rows = state.rows.filter { it.hostId != hostId })
        },
        checkNow = {},
        onOpen = {},
        back = { backs++ }
    )

    private val desktop = PcRow("h1", "DESKTOP-ELT3101", "192.168.1.108", null, PcConnection.AtHome(42))
    private val laptop = PcRow("h2", "LAPTOP", "192.168.1.30", "100.64.0.2", PcConnection.OverVpn(7))

    private fun show(initial: PcsScreenState) {
        state = initial
        rule.setContent {
            DriveCinemaTheme(isTv = true) {
                focusManager = LocalFocusManager.current
                TvPcsScreen(state, actions)
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

    private fun item(key: String): SemanticsNodeInteraction = rule.onNodeWithTag("tv_pcs_item_$key")

    private fun tag(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag(tag)

    @Test
    fun withoutPcsAddAPcStartsFocusedWithItsSteps() {
        show(PcsScreenState())

        item(ADD_PC_KEY).assertIsFocused()
        tag("pcs_search").assertExists()
    }

    @Test
    fun theFirstPcStartsFocusedAndMovingShowsEachItemsPane() {
        show(PcsScreenState(rows = listOf(desktop, laptop)))

        item("h1").assertIsFocused()
        tag("pc_card_h1").assertExists()

        move(FocusDirection.Down)
        item("h2").assertIsFocused()
        tag("pc_card_h2").assertExists()
        tag("pc_card_h1").assertDoesNotExist()

        move(FocusDirection.Down)
        item(ADD_PC_KEY).assertIsFocused()
        tag("pcs_search").assertExists()
        tag("pc_card_h2").assertDoesNotExist()
    }

    @Test
    fun rightEntersThePcsButtonsAndLeftReturnsToTheSamePc() {
        show(PcsScreenState(rows = listOf(desktop, laptop)))
        move(FocusDirection.Down)    // LAPTOP

        move(FocusDirection.Right)
        tag("pc_vpn_edit").assertIsFocused()
        move(FocusDirection.Right)
        tag("pc_check_now").assertIsFocused()
        move(FocusDirection.Right)
        tag("pc_remove").assertIsFocused()

        move(FocusDirection.Left)
        move(FocusDirection.Left)
        tag("pc_vpn_edit").assertIsFocused()
        move(FocusDirection.Left)
        item("h2").assertIsFocused()
    }

    @Test
    fun theAddStepsTakeOneFocusStopEachInStepOrder() {
        show(PcsScreenState(discovered = listOf(DiscoveredHost("h1", "DESKTOP-ELT3101", "192.168.1.20", 8443))))

        move(FocusDirection.Right)
        tag("pcs_search").assertIsFocused()
        move(FocusDirection.Down)
        tag("pcs_found_192.168.1.20").assertIsFocused()
        move(FocusDirection.Down)
        tag("pcs_address").assertIsFocused()
        move(FocusDirection.Down)
        tag("pcs_code").assertIsFocused()
        move(FocusDirection.Down)
        tag("pcs_away").assertIsFocused()

        // Pair can't take the focus until there are 6 characters; then it is the next stop.
        tag("pcs_code").performTextInput("AB3D4E")
        tag("pcs_away").requestFocus()
        rule.waitForIdle()
        move(FocusDirection.Down)
        tag("pcs_pair").assertIsFocused()

        move(FocusDirection.Left)
        item(ADD_PC_KEY).assertIsFocused()
    }

    @Test
    fun upFromTheFirstControlStaysInThePane() {
        show(PcsScreenState())
        move(FocusDirection.Right)
        tag("pcs_search").assertIsFocused()

        move(FocusDirection.Up)

        tag("pcs_search").assertIsFocused()
    }

    @Test
    fun backFromThePaneReturnsToTheItemNotToSettings() {
        show(PcsScreenState(rows = listOf(desktop)))
        move(FocusDirection.Right)
        tag("pc_vpn_edit").assertIsFocused()

        pressBack()

        item("h1").assertIsFocused()
        assertEquals(0, backs)
    }

    @Test
    fun removeAsksFirstThenTheFocusMovesToTheNextItem() {
        show(PcsScreenState(rows = listOf(desktop, laptop)))
        move(FocusDirection.Right)
        move(FocusDirection.Right)
        move(FocusDirection.Right)
        tag("pc_remove").assertIsFocused()

        tag("pc_remove").performClick()
        rule.waitForIdle()
        rule.onNodeWithText("Remove DESKTOP-ELT3101?").assertExists()
        rule.onNode(hasText("Cancel") and hasClickAction()).assertIsFocused()
        assertEquals(emptyList<String>(), removed)

        rule.onNode(hasText("Remove") and hasClickAction() and hasAnyAncestor(hasTestTag("dc_confirm_dialog"))).performClick()
        rule.waitForIdle()

        assertEquals(listOf("h1"), removed)
        item("h2").assertIsFocused()
        tag("pc_card_h2").assertExists()
    }

    @Test
    fun focusedItemsAndStepsAreNotCutOffAtTheScrollEdges() {
        // P.7: the same clipping as in Settings, in both panes.
        show(PcsScreenState(rows = listOf(desktop, laptop)))
        repeat(3) {
            rule.onNode(isFocused()).assertFocusFitsVertically(tag("tv_pcs_list"), what = "item ${it + 1}")
            move(FocusDirection.Down)
        }

        item(ADD_PC_KEY).assertIsFocused()
        move(FocusDirection.Right)
        repeat(8) {
            rule.onNode(isFocused()).assertFocusFitsVertically(tag("tv_pcs_pane"), what = "add step stop ${it + 1}")
            move(FocusDirection.Down)
        }
    }
}
