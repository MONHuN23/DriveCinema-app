package com.drivecinema.app.ui.setup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import android.net.Uri
import com.drivecinema.app.core.local.LocalFolder
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
class AccountDialogLocalFolderTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val account = AccountUiState(
        displayName = "Alex",
        email = "alex@example.com",
        isConnected = true
    )

    @Test
    fun addLocalFolderButtonInvokesCallbackWhenClicked() {
        var addLocalClicks = 0

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                onAddLocalFolderClick = { addLocalClicks++ },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Add Local Folder").assertIsDisplayed().performClick()
        assertEquals(1, addLocalClicks)
    }

    @Test
    fun addLocalFolderButtonHiddenWhenNotOffered() {
        composeTestRule.setContent {
            AccountDialog(
                account = account,
                onAddLocalFolderClick = null,
                onDismissRequest = {}
            )
        }

        composeTestRule.onAllNodesWithText("Add Local Folder").assertCountEquals(0)
    }

    private fun folders(count: Int) = (1..count).map { i ->
        LocalFolder(
            uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies$i"),
            displayName = "Movies $i"
        )
    }

    @Test
    fun removeButtonPassesThatFoldersUri() {
        val saved = folders(2)
        var removed: Uri? = null

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                onAddLocalFolderClick = {},
                savedLocalFolders = saved,
                onRemoveLocalFolderClick = { removed = it },
                onDismissRequest = {}
            )
        }

        composeTestRule.onNodeWithText("Movies 2").performScrollTo().assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Remove")[1].performScrollTo().performClick()
        assertEquals(saved[1].uri, removed)
    }

    @Test
    fun closeStaysReachableWithManySavedFolders() {
        // Regression: the dialog content must scroll so Sign Out / Close remain reachable (D-pad on TV).
        var dismissed = false

        composeTestRule.setContent {
            AccountDialog(
                account = account,
                onSignOutClick = {},
                onAddLocalFolderClick = {},
                savedLocalFolders = folders(15),
                onRemoveLocalFolderClick = {},
                onDismissRequest = { dismissed = true }
            )
        }

        composeTestRule.onNodeWithText("Close").performScrollTo().assertIsDisplayed().performClick()
        assertTrue(dismissed)
    }
}
