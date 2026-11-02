package com.drivecinema.app.ui.details

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.remote.DeviceLink
import com.drivecinema.app.ui.setup.LinkedDevicesDialog
import com.drivecinema.app.ui.setup.LinkedDevicesTexts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Milestone D: "Play on TV" shows only with a linked TV, and the link list removes and adds. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayOnTvUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val movie = MediaItemEntity(id = "m1", type = MediaType.MOVIE, title = "Alien", parentFolderId = "films")

    @Test
    fun movieDetailsHideTheButtonWithoutALinkedTv() {
        composeTestRule.setContent {
            MovieDetailsScreen(movie = movie, progress = null, onPlayClick = {}, onBackClick = {})
        }
        composeTestRule.onAllNodesWithTag("play_on_tv").assertCountEquals(0)
    }

    @Test
    fun movieDetailsSendToTheTv() {
        var sent = 0
        composeTestRule.setContent {
            MovieDetailsScreen(movie = movie, progress = null, onPlayClick = {}, onBackClick = {}, onPlayOnTvClick = { sent++ })
        }
        composeTestRule.onNodeWithTag("play_on_tv").performClick()
        assertEquals(1, sent)
    }

    @Test
    fun linkedDevicesListRemovesAndAdds() {
        val removed = mutableListOf<String>()
        var added = 0
        composeTestRule.setContent {
            LinkedDevicesDialog(
                title = LinkedDevicesTexts.PHONE_TITLE,
                explanation = LinkedDevicesTexts.PHONE_EXPLAIN,
                emptyText = LinkedDevicesTexts.PHONE_EMPTY,
                addLabel = LinkedDevicesTexts.PHONE_ADD,
                devices = listOf(DeviceLink("tv000001", "TCL TV", DeviceLink.newKey())),
                onAdd = { added++ },
                onRemove = { removed += it },
                onDismissRequest = {}
            )
        }
        composeTestRule.onNodeWithText("TCL TV").assertExists()
        composeTestRule.onNodeWithText("Remove").performClick()
        composeTestRule.onNodeWithText(LinkedDevicesTexts.PHONE_ADD).performClick()
        assertEquals(listOf("tv000001"), removed)
        assertEquals(1, added)
    }

    @Test
    fun anEmptyListExplainsHowToLink() {
        composeTestRule.setContent {
            LinkedDevicesDialog(
                title = LinkedDevicesTexts.TV_TITLE,
                explanation = LinkedDevicesTexts.TV_EXPLAIN,
                emptyText = LinkedDevicesTexts.TV_EMPTY,
                addLabel = LinkedDevicesTexts.TV_ADD,
                devices = emptyList(),
                onAdd = {},
                onRemove = {},
                onDismissRequest = {}
            )
        }
        composeTestRule.onNodeWithTag("linked_empty").assertExists()
    }
}
