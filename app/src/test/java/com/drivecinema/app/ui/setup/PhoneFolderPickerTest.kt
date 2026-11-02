package com.drivecinema.app.ui.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.drivecinema.app.core.model.DriveFolder
import com.drivecinema.app.core.model.FolderPickerSections
import com.drivecinema.app.theme.DriveCinemaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** UI polish (Option A): the phone's library picker, its top bar, overflow menu, search and folded shared section. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneFolderPickerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val few = listOf(
        DriveFolder(id = "m1", name = "Streaming"),
        DriveFolder(id = "m2", name = "Beadando"),
        DriveFolder(id = "s1", name = "03_alkalom", ownedByMe = false, ownerName = "nagysz")
    )

    private val many = (1..FolderPickerSections.SEARCH_THRESHOLD).map { DriveFolder(id = "m$it", name = "Course $it") } +
        DriveFolder(id = "mx", name = "Mestintorai") +
        DriveFolder(id = "sx", name = "Mestintorai notes", ownedByMe = false, ownerName = "kitti")

    private fun show(
        folders: List<DriveFolder> = few,
        currentFolderId: String? = null,
        onRefresh: () -> Unit = {},
        onSignOut: () -> Unit = {},
        onCancel: (() -> Unit)? = null
    ) {
        composeTestRule.setContent {
            DriveCinemaTheme {
                FolderPickerScreen(
                    folders = folders,
                    isLoading = false,
                    isValidating = false,
                    onFolderSelected = {},
                    onRefreshClick = onRefresh,
                    onSignOutClick = onSignOut,
                    currentFolderId = currentFolderId,
                    onCancelClick = onCancel,
                    onOpenGuideClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Regression: the picker asked for focus on its first row for the TV's remote, so on the phone that row opened
     * with the focus border and the current folder's "Current" in focus blue instead of its own colour.
     */
    @Test
    fun onThePhoneNoRowStartsFocused() {
        show(currentFolderId = "m1")

        composeTestRule.onNode(hasText("Streaming") and hasClickAction()).assertIsNotFocused()
        composeTestRule.onNodeWithText(PhoneFolderPickerLabels.CURRENT).assertIsDisplayed()
    }

    @Test
    fun atFirstSetupThereIsNoBackArrow() {
        show()

        composeTestRule.onAllNodesWithContentDescription("Back").assertCountEquals(0)
        composeTestRule.onNodeWithText(PhoneFolderPickerLabels.TITLE).assertIsDisplayed()
    }

    @Test
    fun theOverflowMenuRefreshes() {
        var refreshes = 0
        show(onRefresh = { refreshes++ })

        composeTestRule.onNodeWithContentDescription(PhoneFolderPickerLabels.MORE_OPTIONS).performClick()
        composeTestRule.onNodeWithText(PhoneFolderPickerLabels.REFRESH).performClick()
        assertEquals(1, refreshes)
        composeTestRule.onAllNodesWithText(PhoneFolderPickerLabels.REFRESH).assertCountEquals(0)
    }

    @Test
    fun signOutAsksFirst() {
        var signOuts = 0
        show(onSignOut = { signOuts++ })

        composeTestRule.onNodeWithContentDescription(PhoneFolderPickerLabels.MORE_OPTIONS).performClick()
        composeTestRule.onNodeWithText("Sign out").performClick()
        composeTestRule.onNodeWithText("Sign out?").assertIsDisplayed()
        assertEquals(0, signOuts)

        composeTestRule.onNode(hasText("Sign out") and hasAnyAncestor(hasTestTag("dc_confirm_dialog"))).performClick()
        assertEquals(1, signOuts)
    }

    @Test
    fun aFewFoldersNeedNoSearchBox() {
        show()

        composeTestRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test
    fun searchFiltersAndShowsSharedMatchesUnfolded() {
        show(folders = many)

        composeTestRule.onAllNodesWithText("Mestintorai notes").assertCountEquals(0)
        composeTestRule.onNode(hasSetTextAction()).performTextInput("mest")

        composeTestRule.onNodeWithText("Mestintorai").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mestintorai notes").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Course 1").assertCountEquals(0)
    }

    @Test
    fun aSearchWithNoMatchSaysSo() {
        show(folders = many)

        composeTestRule.onNode(hasSetTextAction()).performTextInput("zzz")
        composeTestRule.onNodeWithText("No folders match \"zzz\".").assertIsDisplayed()
    }

    @Test
    fun aSharedCurrentLibraryStaysInSight() {
        show(currentFolderId = "s1")

        composeTestRule.onNodeWithText("03_alkalom").assertIsDisplayed()
        composeTestRule.onNodeWithText(PhoneFolderPickerLabels.CURRENT).assertIsDisplayed()
    }
}
