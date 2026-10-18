package com.drivecinema.app.ui.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.ui.mobile.home.AccountUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AccountDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val account = AccountUiState(displayName = "Jane", email = "jane@example.com", isConnected = true)

    @Test
    fun switchLibraryButtonInvokesCallbackWithoutSigningOut() {
        var switchClicks = 0
        var signOutClicks = 0

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                onSignOutClick = { signOutClicks++ },
                onSwitchLibraryClick = { switchClicks++ },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Switch Library Folder").assertIsDisplayed().performClick()

        assertEquals(1, switchClicks)
        assertEquals(0, signOutClicks)
    }

    @Test
    fun switchLibraryButtonHiddenWhenNotOffered() {
        composeTestRule.setContent {
            AccountDialog(account = account, onSignOutClick = {}, onDismissRequest = {})
        }

        composeTestRule.onAllNodesWithText("Switch Library Folder").assertCountEquals(0)
        composeTestRule.onNodeWithText("Sign Out").assertIsDisplayed()
    }

    @Test
    fun folderPickerInSwitchModeMarksCurrentFolderAndOffersCancel() {
        var cancelled = false
        var selected: DriveFolder? = null
        val folders = listOf(
            DriveFolder(id = "rootA", name = "My Movies"),
            DriveFolder(id = "rootB", name = "Streaming")
        )

        composeTestRule.setContent {
            FolderPickerScreen(
                folders = folders,
                isLoading = false,
                isValidating = false,
                onFolderSelected = { selected = it },
                onRefreshClick = {},
                onSignOutClick = {},
                currentFolderId = "rootA",
                onCancelClick = { cancelled = true }
            )
        }

        composeTestRule.onNodeWithText("✓ Current").assertIsDisplayed()
        composeTestRule.onNodeWithText("Streaming").performClick()
        assertEquals("rootB", selected?.id)
        composeTestRule.onNodeWithText("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun folderPickerShowsIndexingMessageWhileSwitching() {
        composeTestRule.setContent {
            FolderPickerScreen(
                folders = emptyList(),
                isLoading = false,
                isValidating = true,
                onFolderSelected = {},
                onRefreshClick = {},
                onSignOutClick = {},
                busyMessage = "Indexing library folder..."
            )
        }

        composeTestRule.onNodeWithText("Indexing library folder...").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Cancel").assertCountEquals(0)
    }
}
