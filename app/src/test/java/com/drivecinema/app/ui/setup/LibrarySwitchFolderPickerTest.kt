package com.drivecinema.app.ui.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.model.DriveFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The folder picker while switching the library (opened from Settings since Task P.2; from the Account popup before).
 * Settings' own Switch library folder row is tested in `SettingsScreenTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySwitchFolderPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

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

        // The phone (UI polish): the current folder says so, and the top bar's Back cancels the switch.
        composeTestRule.onNodeWithText("Current library").assertIsDisplayed()
        composeTestRule.onNodeWithText("Streaming").performClick()
        assertEquals("rootB", selected?.id)
        composeTestRule.onNodeWithContentDescription("Back").performClick()
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
        composeTestRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
    }
}
