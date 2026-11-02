package com.drivecinema.app.ui.tv.setup

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.settings.LocalFoldersScreen
import com.drivecinema.app.ui.settings.SettingsActions
import com.drivecinema.app.ui.settings.SettingsScreenState
import com.drivecinema.app.ui.settings.TV_NO_FOLDER_PICKER
import com.drivecinema.app.ui.settings.TvSettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * TVs without a system folder picker (Task C.2): since Task P.2 the TV's Settings says so in its Sources pane and
 * offers no Local folders row; TVs with one get the row and the Local folders screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TvStorageCapabilityUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val account = AccountUiState(displayName = "TV User", email = "tv@example.com", isConnected = true)

    private fun actions(openLocalFolders: () -> Unit = {}) = SettingsActions(
        refreshLibrary = {}, switchLibraryFolder = {}, reviewDuplicates = {}, openLocalFolders = openLocalFolders,
        addLocalFolder = {}, removeLocalFolder = {}, openPcs = {}, pairTv = {}, setSkipIntro = {}, openHelp = {},
        openAbout = {}, onOpen = {}, signOut = {}, back = {}
    )

    @Test
    fun tvWithoutAFolderPickerSaysSoInSettingsAndHasNoLocalFoldersRow() {
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = true) {
                TvSettingsScreen(SettingsScreenState(account = account, canAddLocalFolders = false), actions())
            }
        }
        composeTestRule.onNodeWithTag("tv_settings_category_Sources").requestFocus()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(TV_NO_FOLDER_PICKER).assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_settings_row_local").assertDoesNotExist()
        composeTestRule.onNodeWithTag("tv_settings_row_pcs").assertIsDisplayed()
    }

    @Test
    fun tvWithAFolderPickerOffersLocalFolders() {
        var opened = 0
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = true) {
                TvSettingsScreen(
                    SettingsScreenState(account = account, canAddLocalFolders = true, localFolderCount = 2),
                    actions(openLocalFolders = { opened++ })
                )
            }
        }
        composeTestRule.onNodeWithTag("tv_settings_category_Sources").requestFocus()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("2 folders").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tv_settings_row_local").performClick()
        assertEquals(1, opened)
        composeTestRule.onNodeWithTag("tv_settings_no_folder_picker").assertDoesNotExist()
    }

    /** The dialog stays for a TV that reported a picker but couldn't open it. */
    @Test
    fun tvStorageUnsupportedDialogDisplaysAndDismissesOnOk() {
        var dismissed = false

        composeTestRule.setContent {
            TvStorageUnsupportedDialog(
                onDismissRequest = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithText("Local Storage Not Supported").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK").assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun theTvsLocalFoldersScreenListsSavedFoldersWithRemove() {
        val testFolderUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = true) {
                LocalFoldersScreen(
                    folders = listOf(LocalFolder(uri = testFolderUri, displayName = "Movies")),
                    canAdd = true,
                    onAdd = {},
                    onRemove = {},
                    onBack = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Movies").assertIsDisplayed()
        // UI polish: Remove is the row's trash button, named for its folder.
        composeTestRule.onNode(hasContentDescription("Remove Movies") and hasClickAction()).assertIsDisplayed()
        val note = "Removing a folder hides its videos; nothing is deleted and your watch history is kept."
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(note))
        composeTestRule.onNodeWithText(note).assertIsDisplayed()
    }
}
