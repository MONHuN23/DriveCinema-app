package com.drivecinema.app.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.core.host.DiscoveredHost
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.design.DcBannerKind
import com.drivecinema.app.ui.setup.SettingsLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Task P.3: the phone's My PCs screen pairs, edits the Tailscale address and removes a PC as the popup did; Pair says
 * why it can't run yet, and Remove asks first (decision D-P2).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1800dp")
class PcsScreenTest {

    @get:Rule
    val rule = createComposeRule()

    private var state by mutableStateOf(PcsScreenState())
    private val pairs = mutableListOf<Triple<String, String, String>>()
    private val vpnSaves = mutableListOf<Pair<String, String>>()
    private val removed = mutableListOf<String>()
    private var searches = 0
    private var checks = 0

    private val actions = PcsActions(
        search = { searches++ },
        pair = { address, code, vpn -> pairs += Triple(address, code, vpn) },
        setVpnAddress = { hostId, address -> vpnSaves += hostId to address },
        remove = { removed += it },
        checkNow = { checks++ },
        onOpen = {},
        back = {}
    )

    private val desktop = PcRow("h1", "DESKTOP-ELT3101", "192.168.1.108", null, PcConnection.AtHome(42))

    private fun show(initial: PcsScreenState = PcsScreenState()) {
        state = initial
        rule.setContent { DriveCinemaTheme { PcsScreen(state, actions) } }
        rule.waitForIdle()
    }

    private fun tag(tag: String): SemanticsNodeInteraction = rule.onNodeWithTag(tag)

    /** The banner's text (the banner itself doesn't merge its children). */
    private fun notice(): SemanticsNodeInteraction =
        rule.onNode(hasAnyAncestor(hasTestTag("pcs_notice")) and hasText("", substring = true))

    private fun dialogButton(text: String) =
        rule.onNode(hasText(text) and hasClickAction() and hasAnyAncestor(hasTestTag("dc_confirm_dialog")))

    @Test
    fun pairIsDisabledUntilThereIsAnAddressAndAllSixCharactersAndSaysWhy() {
        show()

        tag("pcs_pair").assertIsNotEnabled()
        tag("pcs_pair_reason").assertTextContains("Find the PC, or type its address")

        tag("pcs_address").performTextInput("192.168.1.20")
        tag("pcs_pair").assertIsNotEnabled()
        tag("pcs_pair_reason").assertTextContains("Enter all 6 characters of the code")

        tag("pcs_code").performTextInput("ab3d")
        tag("pcs_pair").assertIsNotEnabled()
        tag("pcs_code").performTextInput("4e5")
        tag("pcs_code").assert(hasText("AB3D4E"))
        tag("pcs_pair").assertIsEnabled()
        tag("pcs_pair_reason").assertDoesNotExist()

        tag("pcs_pair").performClick()
        assertEquals(listOf(Triple("192.168.1.20", "AB3D4E", "")), pairs)
    }

    @Test
    fun theCodeKeepsLettersAndDigitsInCapitalsUpToSix() {
        assertEquals("AB3D4E", pairingCodeInput("ab-3d 4e5"))
        assertEquals("", pairingCodeInput(" -"))
        assertEquals("Find the PC, or type its address", pairBlockedReason(" ", "AB3D4E"))
        assertEquals("Enter all 6 characters of the code", pairBlockedReason("10.0.0.5", "AB3D4"))
        assertNull(pairBlockedReason("10.0.0.5", "AB3D4E"))
    }

    @Test
    fun aFoundPcFillsTheAddressAndAFoundRowChoosesIt() {
        show()
        tag("pcs_search").performClick()
        assertEquals(1, searches)

        state = state.copy(
            discovered = listOf(
                DiscoveredHost("h1", "DESKTOP-ELT3101", "192.168.1.20", 8443),
                DiscoveredHost("h2", "LAPTOP", "192.168.1.30", 8443)
            )
        )
        rule.waitForIdle()
        tag("pcs_address").assert(hasText("192.168.1.20"))

        tag("pcs_found_192.168.1.30").performClick()
        tag("pcs_address").assert(hasText("192.168.1.30"))
    }

    @Test
    fun aSearchNeverOverwritesATypedAddress() {
        show()
        tag("pcs_address").performTextInput("10.0.0.5")

        state = state.copy(discovered = listOf(DiscoveredHost("h1", "DESKTOP-ELT3101", "192.168.1.20", 8443)))
        rule.waitForIdle()

        tag("pcs_address").assert(hasText("10.0.0.5"))
    }

    @Test
    fun theTailscaleAddressIsFoldedAwayAndGoesWithThePairing() {
        show()
        tag("pcs_vpn").assertDoesNotExist()

        tag("pcs_away").performClick()
        tag("pcs_vpn").performTextInput("100.101.102.103")
        tag("pcs_address").performTextInput("192.168.1.20")
        tag("pcs_code").performTextInput("AB3D4E")
        tag("pcs_pair").performClick()

        assertEquals(listOf(Triple("192.168.1.20", "AB3D4E", "100.101.102.103")), pairs)
    }

    @Test
    fun afterAPairingTheFormEmpties() {
        show()
        tag("pcs_address").performTextInput("192.168.1.20")
        tag("pcs_code").performTextInput("AB3D4E")

        state = state.copy(pairedCount = 1, notice = PcsNotice("Paired with DESKTOP-ELT3101. Loading its films…"))
        rule.waitForIdle()

        tag("pcs_code").assert(hasText("AB3D4E").not())
        tag("pcs_address").assert(hasText("192.168.1.20").not())
        notice().assertTextContains("Paired with DESKTOP-ELT3101", substring = true)
        tag("pcs_pair").assertIsNotEnabled()
    }

    @Test
    fun aCardShowsTheStateTheVideosAndTheAddresses() {
        show(
            PcsScreenState(
                rows = listOf(desktop, PcRow("h2", "LAPTOP", null, "100.64.0.2", PcConnection.Unreachable))
            )
        )

        rule.onNodeWithText("Connected at home").assertExists()
        rule.onNodeWithText("42 videos").assertExists()
        rule.onNodeWithText("192.168.1.108").assertExists()
        rule.onNodeWithText("Not set; needed away from home").assertExists()
        rule.onNodeWithText("Not reachable").assertExists()
        rule.onNodeWithText("Off, or away from home without the VPN").assertExists()
        rule.onNodeWithText("100.64.0.2").assertExists()
    }

    @Test
    fun checkNowShowsOnlyWithPcsAndRunsTheCheck() {
        show()
        tag("pcs_check_now").assertDoesNotExist()

        state = PcsScreenState(rows = listOf(desktop))
        rule.waitForIdle()
        tag("pcs_check_now").performClick()

        assertEquals(1, checks)
    }

    @Test
    fun settingTheTailscaleAddressSavesIt() {
        show(PcsScreenState(rows = listOf(desktop)))

        tag("pc_vpn_edit").assertTextContains(SettingsLabels.SET_TAILSCALE_ADDRESS).performClick()
        tag("pc_vpn_field").performTextInput("100.101.102.103")
        tag("pc_vpn_save").performClick()

        assertEquals(listOf("h1" to "100.101.102.103"), vpnSaves)
        tag("pc_vpn_field").assertDoesNotExist()
    }

    @Test
    fun changingStartsFromTheSavedAddressAndEmptyClearsIt() {
        show(PcsScreenState(rows = listOf(desktop.copy(vpnAddress = "100.64.0.9"))))

        tag("pc_vpn_edit").assertTextContains("Change Tailscale address").performClick()
        tag("pc_vpn_field").assert(hasText("100.64.0.9"))
        tag("pc_vpn_field").performTextClearance()
        tag("pc_vpn_save").performClick()

        assertEquals(listOf("h1" to ""), vpnSaves)
    }

    @Test
    fun removeAsksFirstAndCancelKeepsThePc() {
        show(PcsScreenState(rows = listOf(desktop)))

        tag("pc_remove").performClick()
        rule.onNodeWithText("Remove DESKTOP-ELT3101?").assertExists()
        rule.onNodeWithText(REMOVE_PC_TEXT).assertExists()
        assertEquals(emptyList<String>(), removed)

        dialogButton("Cancel").performClick()
        tag("dc_confirm_dialog").assertDoesNotExist()
        assertEquals(emptyList<String>(), removed)

        tag("pc_remove").performClick()
        dialogButton("Remove").performClick()
        assertEquals(listOf("h1"), removed)
    }

    @Test
    fun aNoteIsShownAtTheTop() {
        show(PcsScreenState(notice = PcsNotice("Pairing failed. Try again.", DcBannerKind.Error)))

        notice().assertTextContains("Pairing failed. Try again.")
    }
}
