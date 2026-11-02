package com.drivecinema.app.ui.settings

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.setup.SettingsLabels
import com.drivecinema.app.ui.setup.SkipIntroTexts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task P.2: the phone's Settings screen does everything the Account popup did, and Sign out asks first (D-P2). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val connected = AccountUiState(displayName = "Jane", email = "jane@example.com", isConnected = true)

    /** How often each action ran. */
    private val calls = mutableMapOf<String, Int>()

    private fun count(name: String): () -> Unit = { calls[name] = (calls[name] ?: 0) + 1 }

    private fun calls(name: String) = calls[name] ?: 0

    private val actions = SettingsActions(
        refreshLibrary = count("refresh"),
        switchLibraryFolder = count("switch"),
        reviewDuplicates = count("duplicates"),
        openLocalFolders = count("localFolders"),
        addLocalFolder = count("add"),
        removeLocalFolder = {},
        openPcs = count("pcs"),
        pairTv = count("pairTv"),
        setSkipIntro = {},
        openHelp = count("help"),
        openAbout = count("about"),
        onOpen = count("open"),
        signOut = count("signOut"),
        back = count("back")
    )

    private fun show(state: SettingsScreenState, actions: SettingsActions = this.actions) {
        composeTestRule.setContent { DriveCinemaTheme { SettingsScreen(state, actions) } }
    }

    private fun row(title: String): SemanticsNodeInteraction {
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasText(title))
        return composeTestRule.onNode(hasText(title) and hasClickAction())
    }

    @Test
    fun switchLibraryFolderOpensThePickerWithoutSigningOut() {
        show(SettingsScreenState(account = connected))

        row(SettingsLabels.SWITCH_LIBRARY_FOLDER).assertIsDisplayed().performClick()

        assertEquals(1, calls("switch"))
        assertEquals(0, calls("signOut"))
    }

    @Test
    fun withoutAConnectedDriveOnlyTheRowsThatStillWorkAreShown() {
        show(SettingsScreenState(account = AccountUiState(isConnected = false)))

        composeTestRule.onAllNodesWithText(SettingsLabels.SWITCH_LIBRARY_FOLDER).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(SettingsLabels.REFRESH_LIBRARY).assertCountEquals(0)
        composeTestRule.onNodeWithText("Not connected").assertIsDisplayed()
        row(SettingsLabels.SIGN_OUT).assertIsDisplayed()
    }

    @Test
    fun duplicatesAreOfferedOnlyWhenThereAreSome() {
        show(SettingsScreenState(account = connected, pendingDuplicateCount = 0))
        composeTestRule.onAllNodesWithText(SettingsLabels.REVIEW_DUPLICATES).assertCountEquals(0)
    }

    @Test
    fun duplicatesRowShowsTheCountAndOpensTheReview() {
        show(SettingsScreenState(account = connected, pendingDuplicateCount = 2))

        composeTestRule.onNodeWithText("2 titles to check").assertIsDisplayed()
        row(SettingsLabels.REVIEW_DUPLICATES).performClick()
        assertEquals(1, calls("duplicates"))
    }

    @Test
    fun refreshWaitsWhileARefreshRuns() {
        var refreshing by mutableStateOf(true)
        composeTestRule.setContent {
            DriveCinemaTheme { SettingsScreen(SettingsScreenState(account = connected, isRefreshingLibrary = refreshing), actions) }
        }

        composeTestRule.onNodeWithText("Refreshing…").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_refresh").performClick()
        assertEquals(0, calls("refresh"))

        refreshing = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_refresh").performClick()
        assertEquals(1, calls("refresh"))
    }

    @Test
    fun signOutAsksFirstAndCancelKeepsYouSignedIn() {
        show(SettingsScreenState(account = connected))

        row(SettingsLabels.SIGN_OUT).performClick()
        composeTestRule.onNodeWithText("Sign out of DriveCinema?").assertIsDisplayed()
        composeTestRule.onNodeWithText(SIGN_OUT_QUESTION).assertIsDisplayed()
        assertEquals(0, calls("signOut"))

        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onAllNodesWithText("Sign out of DriveCinema?").assertCountEquals(0)
        assertEquals(0, calls("signOut"))

        row(SettingsLabels.SIGN_OUT).performClick()
        composeTestRule.onNode(hasText(SettingsLabels.SIGN_OUT) and hasAnyAncestor(hasTestTag("dc_confirm_dialog"))).performClick()
        assertEquals(1, calls("signOut"))
    }

    /** Milestone O: one tap or one OK press flips the switch once (as the Account popup's switch did). */
    @Test
    fun skipIntroFlipsOncePerTapAndPerOkPress() {
        var enabled by mutableStateOf(true)
        composeTestRule.setContent {
            DriveCinemaTheme {
                SettingsScreen(
                    SettingsScreenState(account = connected, skipIntroEnabled = enabled),
                    SettingsActions(
                        refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = {},
                        addLocalFolder = {}, removeLocalFolder = {}, openPcs = {}, pairTv = {},
                        setSkipIntro = { enabled = it }, openHelp = {}, openAbout = {}, onOpen = {}, signOut = {}, back = {}
                    )
                )
            }
        }
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_skip_intro_switch"))
        val switch = composeTestRule.onNodeWithTag("settings_skip_intro_switch")
        switch.assertIsOn()
        composeTestRule.onNodeWithText(SkipIntroTexts.SWITCH_CAPTION).assertIsDisplayed()

        switch.performClick()
        assertFalse(enabled)
        switch.assertIsOff()

        switch.requestFocus()
        switch.performKeyPress(androidx.compose.ui.input.key.KeyEvent(NativeKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER)))
        switch.performKeyPress(androidx.compose.ui.input.key.KeyEvent(NativeKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER)))
        assertTrue(enabled)
    }

    @Test
    fun sourcesAndHelpRowsOpenTheirScreens() {
        show(SettingsScreenState(account = connected, localFolderCount = 1, pcSummary = "DESKTOP-ELT3101 · Connected at home"))

        val localFolders = row(SettingsLabels.LOCAL_FOLDERS)
        composeTestRule.onNodeWithText("1 folder").assertIsDisplayed()
        localFolders.performClick()
        val pcs = row(SettingsLabels.MY_PCS)
        composeTestRule.onNodeWithText("DESKTOP-ELT3101 · Connected at home").assertIsDisplayed()
        pcs.performClick()
        row(SettingsLabels.PAIR_A_TV).performClick()
        row(SettingsLabels.HELP_CENTER).performClick()
        row(SettingsLabels.ABOUT).performClick()

        assertEquals(1, calls("localFolders"))
        assertEquals(1, calls("pcs"))
        assertEquals(1, calls("pairTv"))
        assertEquals(1, calls("help"))
        assertEquals(1, calls("about"))
    }

    @Test
    fun theIntroCreditsAreShown() {
        show(SettingsScreenState(account = connected))

        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasText(SkipIntroTexts.ATTRIBUTION))
        composeTestRule.onNodeWithText(SkipIntroTexts.ATTRIBUTION).assertIsDisplayed()
    }

    @Test
    fun theMyPcsLineNamesThePcAndItsState() {
        assertEquals("Play videos stored on a PC", pcSummary(emptyList()))
        assertEquals(
            "DESKTOP-ELT3101 · Connected at home",
            pcSummary(listOf(PcRow("h1", "DESKTOP-ELT3101", "192.168.1.108", null, PcConnection.AtHome(8))))
        )
        assertEquals(
            "PC · Connected through the VPN",
            pcSummary(listOf(PcRow("h1", "PC", null, "100.64.0.2", PcConnection.OverVpn(8))))
        )
        assertEquals("PC · Not reachable", pcSummary(listOf(PcRow("h1", "PC", null, null, PcConnection.Unreachable))))
        assertEquals("PC", pcSummary(listOf(PcRow("h1", "PC", null, null))))
        assertEquals("2 PCs", pcSummary(listOf(PcRow("a", "A", null, null), PcRow("b", "B", null, null))))
    }

    @Test
    fun theLocalFoldersLineSaysHowManyOrWhyNot() {
        assertEquals("Add folders from this phone's storage", localFolderSummary(0, canAdd = true, isTv = false))
        assertEquals("1 folder", localFolderSummary(1, canAdd = true, isTv = false))
        assertEquals("3 folders", localFolderSummary(3, canAdd = true, isTv = true))
        assertEquals("Not supported on this TV", localFolderSummary(0, canAdd = false, isTv = true))
    }

    @Test
    fun theGuideNamesTheSettingsScreensPlaces() {
        assertEquals("Settings → Local folders → Add folder", SettingsLabels.path(SettingsLabels.LOCAL_FOLDERS, SettingsLabels.ADD_FOLDER))
        val guide = com.drivecinema.app.ui.setup.SetupGuideContent.sections.flatMap { it.text }
        assertTrue(guide.none { "Account →" in it })
        assertTrue(guide.any { "Settings → My PCs" in it })
    }

    /** Milestone Q: the switch sits in Library, says what it does, and later how the last sync went. */
    @Test
    fun theDeviceSyncSwitchShowsItsStateAndStatus() {
        var enabled by mutableStateOf(false)
        var status by mutableStateOf<String?>(null)
        composeTestRule.setContent {
            DriveCinemaTheme {
                SettingsScreen(
                    SettingsScreenState(account = connected, deviceSyncEnabled = enabled, deviceSyncStatus = status),
                    SettingsActions(
                        refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = {},
                        addLocalFolder = {}, removeLocalFolder = {}, openPcs = {}, pairTv = {}, setSkipIntro = {},
                        setDeviceSync = { enabled = it },
                        openHelp = {}, openAbout = {}, onOpen = {}, signOut = {}, back = {}
                    )
                )
            }
        }
        composeTestRule.onNodeWithTag("settings_list").performScrollToNode(hasTestTag("settings_device_sync_switch"))
        val switch = composeTestRule.onNodeWithTag("settings_device_sync_switch")
        switch.assertIsOff()
        composeTestRule.onNodeWithText(com.drivecinema.app.ui.setup.DeviceSyncTexts.SWITCH_CAPTION).assertIsDisplayed()
        switch.performClick()
        assertTrue(enabled)
        switch.assertIsOn()
        status = "Last synced at 12:00"
        composeTestRule.onNodeWithText("Last synced at 12:00").assertIsDisplayed()
    }

    @Test
    fun withoutAConnectedDriveThereIsNoSyncSwitch() {
        show(SettingsScreenState(account = AccountUiState(isConnected = false)))
        composeTestRule.onAllNodesWithText(SettingsLabels.SYNC_WITH_MY_DEVICES).assertCountEquals(0)
    }

    /** Milestone D: the phone's Play on TV links open from Settings → Sources. */
    @Test
    fun myTvsOpensTheLinkedTvs() {
        var opened = 0
        show(
            SettingsScreenState(account = connected),
            SettingsActions(
                refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = {},
                addLocalFolder = {}, removeLocalFolder = {}, openPcs = {}, pairTv = {}, setSkipIntro = {},
                openLinkedDevices = { opened++ },
                openHelp = {}, openAbout = {}, onOpen = {}, signOut = {}, back = {}
            )
        )
        row(SettingsLabels.MY_TVS).assertIsDisplayed().performClick()
        assertEquals(1, opened)
    }
}
