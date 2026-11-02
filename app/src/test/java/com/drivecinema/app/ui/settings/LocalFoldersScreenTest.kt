package com.drivecinema.app.ui.settings

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.theme.DriveCinemaTheme
import com.drivecinema.app.ui.setup.SettingsLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task P.2: the saved local folders, on their own screen (they were listed inside the Account popup). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalFoldersScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun folders(count: Int) = (1..count).map { i ->
        LocalFolder(
            uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies$i"),
            displayName = "Movies $i"
        )
    }

    @Test
    fun addFolderOpensThePicker() {
        var adds = 0
        composeTestRule.setContent {
            DriveCinemaTheme { LocalFoldersScreen(emptyList(), canAdd = true, onAdd = { adds++ }, onRemove = {}, onBack = {}) }
        }

        // UI polish: an empty state explains the Films/Series layout, with one Add folder button.
        composeTestRule.onNodeWithTag("local_folders_empty").assertIsDisplayed()
        composeTestRule.onNodeWithText("Films").assertIsDisplayed()
        composeTestRule.onNodeWithText("Series").assertIsDisplayed()
        composeTestRule.onNodeWithText(SettingsLabels.ADD_FOLDER).performScrollTo().performClick()
        assertEquals(1, adds)
    }

    @Test
    fun removeAsksFirstThenRemovesThatFolder() {
        val saved = folders(2)
        var removed: Uri? = null
        composeTestRule.setContent {
            DriveCinemaTheme { LocalFoldersScreen(saved, canAdd = true, onAdd = {}, onRemove = { removed = it }, onBack = {}) }
        }

        composeTestRule.onNodeWithText("Movies 2").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove Movies 2").performClick()
        composeTestRule.onNodeWithText("Remove \"Movies 2\"?").assertIsDisplayed()
        assertNull(removed)

        composeTestRule.onNode(hasText("Remove") and hasAnyAncestor(hasTestTag("dc_confirm_dialog"))).performClick()
        assertEquals(saved[1].uri, removed)
    }

    @Test
    fun cancellingTheQuestionKeepsTheFolder() {
        var removed: Uri? = null
        composeTestRule.setContent {
            DriveCinemaTheme { LocalFoldersScreen(folders(1), canAdd = true, onAdd = {}, onRemove = { removed = it }, onBack = {}) }
        }

        composeTestRule.onNodeWithContentDescription("Remove Movies 1").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onAllNodesWithText("Remove \"Movies 1\"?").assertCountEquals(0)
        assertNull(removed)
    }

    /** Regression (was `closeStaysReachableWithManySavedFolders`): a long list scrolls, every folder stays reachable. */
    @Test
    fun theLastOfManyFoldersCanBeReached() {
        composeTestRule.setContent {
            DriveCinemaTheme { LocalFoldersScreen(folders(15), canAdd = true, onAdd = {}, onRemove = {}, onBack = {}) }
        }

        composeTestRule.onNodeWithText("Movies 15").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Remove Movies 15").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun aTvWithoutAFolderPickerSaysWhyAndOffersNoAdd() {
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = true) { LocalFoldersScreen(emptyList(), canAdd = false, onAdd = {}, onRemove = {}, onBack = {}) }
        }

        composeTestRule.onNodeWithText(TV_NO_FOLDER_PICKER).performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText(SettingsLabels.ADD_FOLDER).assertCountEquals(0)
    }

    @Test
    fun eachFolderSaysWhereItIsAndWhatTheScanFound() {
        val saved = folders(3)
        composeTestRule.setContent {
            DriveCinemaTheme {
                LocalFoldersScreen(
                    saved, canAdd = true, onAdd = {}, onRemove = {}, onBack = {},
                    videoCounts = mapOf(saved[0].uri.toString() to 24, saved[1].uri.toString() to 1),
                    isScanning = false
                )
            }
        }

        composeTestRule.onNodeWithText("Internal storage › Movies1\n24 videos").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal storage › Movies2\n1 video").assertIsDisplayed()
        composeTestRule.onNodeWithText("Internal storage › Movies3\nCouldn't read this folder").assertIsDisplayed()
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(ADD_ANOTHER_FOLDER))
        composeTestRule.onNodeWithText(ADD_ANOTHER_FOLDER).assertIsDisplayed()
    }

    @Test
    fun aFolderTheScanHasNotReachedSaysScanning() {
        composeTestRule.setContent {
            DriveCinemaTheme { LocalFoldersScreen(folders(1), canAdd = true, onAdd = {}, onRemove = {}, onBack = {}, isScanning = true) }
        }

        composeTestRule.onNodeWithText("Scanning…", substring = true).assertIsDisplayed()
    }

    @Test
    fun theEmptyStateLinksToTheNamingGuide() {
        var opened = 0
        composeTestRule.setContent {
            DriveCinemaTheme {
                LocalFoldersScreen(emptyList(), canAdd = true, onAdd = {}, onRemove = {}, onBack = {}, onOpenGuide = { opened++ })
            }
        }

        composeTestRule.onNodeWithText(HOW_TO_NAME_FILES).performScrollTo().performClick()
        assertEquals(1, opened)
    }

    @Test
    fun onTheTvTheRemoteStartsOnAddFolder() {
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = true) { LocalFoldersScreen(folders(1), canAdd = true, onAdd = {}, onRemove = {}, onBack = {}) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("local_folders_add").assertIsFocused()
    }
}
