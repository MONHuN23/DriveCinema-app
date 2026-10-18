package com.drivecinema.app.ui.setup

import com.drivecinema.app.core.model.FolderPickerSections
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        list.performScrollToNode(hasText("SHARED WITH YOU"))
        composeTestRule.onNodeWithText("SHARED WITH YOU").assertIsDisplayed()
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
        composeTestRule.onNodeWithText("? Setup guide").performClick()
        assertEquals(2, guideOpened)
    }

    @Test
    fun setupGuideShowsEverySectionAndGoesBack() {
        var back = false
        composeTestRule.setContent { SetupGuideScreen(onBackClick = { back = true }) }

        SetupGuideContent.sections.forEach { section ->
            composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(section.title))
            composeTestRule.onNodeWithText(section.title).assertIsDisplayed()
        }
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("← Back"))
        composeTestRule.onNodeWithText("← Back").performClick()
        assertTrue(back)
    }

    @Test
    fun accountDialogOpensTheSetupGuide() {
        var opened = false
        composeTestRule.setContent {
            AccountDialog(
                account = com.drivecinema.app.ui.mobile.home.AccountUiState(isConnected = true),
                onOpenGuideClick = { opened = true },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Setup Guide").performClick()
        assertTrue(opened)
    }
}
