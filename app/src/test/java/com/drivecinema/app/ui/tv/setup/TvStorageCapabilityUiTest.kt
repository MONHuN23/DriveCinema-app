package com.drivecinema.app.ui.tv.setup

import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.local.LocalFolder
import com.drivecinema.app.ui.mobile.home.AccountUiState
import com.drivecinema.app.ui.setup.AccountDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TvStorageCapabilityUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val account = AccountUiState(
        displayName = "TV User",
        email = "tv@example.com",
        isConnected = true
    )

    @Test
    fun tvUnsupportedStorageShowsClearMessageAndTriggersDialogCallback() {
        var unsupportedClicked = 0

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                isTvDevice = true,
                isTvStorageSupported = false,
                onTvStorageUnsupportedClick = { unsupportedClicked++ },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Local Storage (Not Supported)").assertIsDisplayed().performClick()
        assertEquals(1, unsupportedClicked)
        composeTestRule.onNodeWithText("Local folder selection is not supported by this TV firmware").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Add Local Folder").assertCountEquals(0)
    }

    @Test
    fun tvSupportedStorageShowsAddLocalFolderButton() {
        var addLocalClicked = 0

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                isTvDevice = true,
                isTvStorageSupported = true,
                onAddLocalFolderClick = { addLocalClicked++ },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Add Local Folder").assertIsDisplayed().performClick()
        assertEquals(1, addLocalClicked)
        composeTestRule.onAllNodesWithText("Local Storage (Not Supported)").assertCountEquals(0)
    }

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
    fun accountDialogRendersSavedFoldersWithRemoveAction() {
        var removedUri: Uri? = null
        val testFolderUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")
        val savedFolders = listOf(
            LocalFolder(uri = testFolderUri, displayName = "Movies")
        )

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                savedLocalFolders = savedFolders,
                onRemoveLocalFolderClick = { removedUri = it },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Movies").assertIsDisplayed()
        composeTestRule.onNodeWithText("Videos in these folders appear in your library and play from this device").assertIsDisplayed()
        composeTestRule.onNodeWithText("Remove").assertIsDisplayed().performClick()
        assertEquals(testFolderUri, removedUri)
    }
}
