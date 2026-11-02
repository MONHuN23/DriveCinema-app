package com.drivecinema.app.ui.setup

import com.drivecinema.app.core.model.FolderPickerSections
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import com.drivecinema.app.theme.DriveCinemaTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollToNodeAction
import com.drivecinema.app.core.model.DriveFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FolderPickerAndGuideUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val folders = listOf(
        DriveFolder(id = "m2", name = "Old Movies"),
        DriveFolder(id = "m1", name = "Streaming"),
        DriveFolder(id = "s1", name = "Streaming", ownedByMe = false, ownerName = "Janos"),
        DriveFolder(id = "doc", name = "notes.txt", isFolder = false, mimeType = "text/plain")
    )

    @Test
    fun sectionsPutOwnFoldersFirstAndStreamingOnTop() {
        val sections = FolderPickerSections.from(folders)

        assertEquals(listOf("m1", "m2"), sections.mine.map { it.id })
        assertEquals(listOf("s1"), sections.shared.map { it.id })
        assertTrue("Files are never offered as libraries", (sections.mine + sections.shared).none { it.id == "doc" })
    }

    @Test
    fun pickerLabelsOwnAndSharedFoldersWithoutRawIds() {
        var selected: DriveFolder? = null
        composeTestRule.setContent {
            FolderPickerScreen(
                folders = folders,
                isLoading = false,
                isValidating = false,
                onFolderSelected = { selected = it },
                onRefreshClick = {},
                onSignOutClick = {}
            )
        }

        val list = composeTestRule.onNode(hasScrollToNodeAction())
        composeTestRule.onNodeWithText("YOUR DRIVE").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("ID:", substring = true).assertCountEquals(0)
        // The phone folds "Shared with you" away while there are folders of your own; one tap opens it.
        list.performScrollToNode(hasText("Shared with you"))
        composeTestRule.onAllNodesWithText("Shared by Janos").assertCountEquals(0)
        composeTestRule.onNodeWithText("Shared with you").performClick()
        list.performScrollToNode(hasText("Shared by Janos"))
        composeTestRule.onNodeWithText("Shared by Janos").assertIsDisplayed()
        assertTrue(FolderPickerSections.from(folders).let { s -> (s.mine + s.shared).count { FolderPickerSections.isRecommended(it) } } == 2)

        composeTestRule.onNodeWithText("Shared by Janos").performClick()
        assertEquals("s1", selected?.id)
    }

    @Test
    fun pickerOffersTheSetupGuide() {
        var guideOpened = 0
        composeTestRule.setContent {
            FolderPickerScreen(
                folders = emptyList(),
                isLoading = false,
                isValidating = false,
                onFolderSelected = {},
                onRefreshClick = {},
                onSignOutClick = {},
                onOpenGuideClick = { guideOpened++ }
            )
        }

        composeTestRule.onNodeWithText("No folders found").assertIsDisplayed()
        composeTestRule.onNodeWithText("How to set up folders").performClick()
        composeTestRule.onNodeWithContentDescription("More options").performClick()
        composeTestRule.onNodeWithText("Setup guide").performClick()
        assertEquals(2, guideOpened)
    }

    /** The TV keeps its own picker: the action row above the list, the remote starting on the first folder. */
    @Test
    fun theTvKeepsItsActionRowAndStartsOnTheFirstFolder() {
        var guideOpened = 0
        composeTestRule.setContent {
            DriveCinemaTheme(isTv = true) {
                FolderPickerScreen(
                    folders = folders,
                    isLoading = false,
                    isValidating = false,
                    onFolderSelected = {},
                    onRefreshClick = {},
                    onSignOutClick = {},
                    onOpenGuideClick = { guideOpened++ }
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithContentDescription("More options").assertCountEquals(0)
        composeTestRule.onNodeWithText("Sign Out").assertExists()
        composeTestRule.onNodeWithText("? Setup guide").performClick()
        assertEquals(1, guideOpened)
    }

    // The Help Center itself (the setup guide before Task P.4) is tested in ui/help/HelpScreensTest and
    // TvHelpFocusTest; the picker's link opening "Set up your folders" in PredictiveBackNavigationTest. Settings' Help
    // Center row (the Account popup's "Setup Guide" before Task P.2) in SettingsScreenTest.sourcesAndHelpRowsOpenTheirScreens.
}
