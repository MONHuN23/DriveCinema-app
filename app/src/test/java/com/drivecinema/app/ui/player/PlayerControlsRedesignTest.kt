package com.drivecinema.app.ui.player

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The player controls redesign: Previous and Play/Pause under the timeline, Details, and the TV focus order (Variant B). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerControlsRedesignTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var focusManager: FocusManager

    private fun showOverlay(
        hasPrevious: () -> Boolean = { true },
        nextTitle: () -> String? = { "Dark - S1E3 Past and Present" },
        isEnded: Boolean = false,
        onDetails: (() -> Unit)? = {},
        onPrevious: () -> Unit = {},
        onPlayPause: () -> Unit = {},
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            focusManager = LocalFocusManager.current
            PlaybackOverlay(
                title = "Dark - S1E2 Lies",
                isPlaying = true,
                currentPositionMs = 60_000L,
                durationMs = 2_700_000L,
                audioTracks = emptyList(),
                nextEpisodeTitle = nextTitle(),
                isEnded = isEnded,
                onPlayPauseClick = onPlayPause,
                onSeekRelative = {},
                onSeekTo = {},
                onAudioTrackClick = {},
                hasPreviousEpisode = hasPrevious(),
                onPreviousEpisodeClick = onPrevious,
                onDetailsClick = onDetails,
                onBackPressed = onBack
            )
        }
    }

    private fun move(direction: FocusDirection) {
        composeTestRule.runOnIdle { focusManager.moveFocus(direction) }
        composeTestRule.waitForIdle()
    }

    @Test
    fun previousAndBottomPlayPauseSitUnderTheTimeline() {
        var previous = 0
        var playPause = 0
        showOverlay(onPrevious = { previous++ }, onPlayPause = { playPause++ })

        composeTestRule.onNodeWithTag("player_previous_episode_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertIsDisplayed().performClick()
        assertEquals(1, previous)
        assertEquals(1, playPause)
        // The centre row stays (Variant B), on the TV too.
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_rewind_button").assertIsDisplayed()
    }

    @Test
    fun previousShowsOnlyWhenThereIsAPreviousEpisode() {
        showOverlay(hasPrevious = { false })
        composeTestRule.onNodeWithTag("player_previous_episode_button").assertDoesNotExist()
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertIsDisplayed()
    }

    @Test
    fun detailsShowsWhenTheItemIsKnownAndOpensIt() {
        var details = 0
        showOverlay(onDetails = { details++ })
        composeTestRule.onNodeWithTag("player_details_button").assertIsDisplayed().performClick()
        assertEquals(1, details)
    }

    @Test
    fun noDetailsButtonWithoutAnItem() {
        showOverlay(onDetails = null)
        composeTestRule.onNodeWithTag("player_details_button").assertDoesNotExist()
    }

    @Test
    fun theEndCardsDetailsReallyOpensDetails() {
        var details = 0
        var back = 0
        showOverlay(nextTitle = { null }, isEnded = true, onDetails = { details++ }, onBack = { back++ })
        composeTestRule.onNodeWithTag("player_return_to_details_button").assertIsFocused().performClick()
        assertEquals(1, details)
        assertEquals("Details doesn't just go back", 0, back)
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertDoesNotExist()
    }

    @Test
    fun dpadDownWalksCentreThenNextThenTimelineThenBottomRow() {
        showOverlay()
        composeTestRule.onNodeWithTag("player_play_pause_button").requestFocus()

        move(FocusDirection.Down)
        composeTestRule.onNodeWithTag("player_next_episode_button").assertIsFocused()
        move(FocusDirection.Down)
        composeTestRule.onNodeWithTag("player_timeline_scrubber").assertIsFocused()
        move(FocusDirection.Down)
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertIsFocused()

        move(FocusDirection.Left)
        composeTestRule.onNodeWithTag("player_previous_episode_button").assertIsFocused()
        move(FocusDirection.Right)
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertIsFocused()
        move(FocusDirection.Right)
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertIsFocused()

        move(FocusDirection.Up)
        composeTestRule.onNodeWithTag("player_timeline_scrubber").assertIsFocused()
        move(FocusDirection.Up)
        composeTestRule.onNodeWithTag("player_next_episode_button").assertIsFocused()
        move(FocusDirection.Up)
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsFocused()
    }

    @Test
    fun withoutNextEpisodeTheCentreRowGoesStraightToTheTimeline() {
        showOverlay(nextTitle = { null })
        composeTestRule.onNodeWithTag("player_play_pause_button").requestFocus()
        move(FocusDirection.Down)
        composeTestRule.onNodeWithTag("player_timeline_scrubber").assertIsFocused()
        move(FocusDirection.Up)
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsFocused()
    }

    @Test
    fun topBarDownGoesToTheCentrePlayPause() {
        showOverlay()
        composeTestRule.onNodeWithTag("player_details_button").requestFocus()
        move(FocusDirection.Down)
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsFocused()
    }

    /** The F-32 lesson: a focused button that disappears hands the focus on, so OK never reaches nothing. */
    @Test
    fun whenTheFocusedPreviousDisappearsFocusMovesToPlayPause() {
        var hasPrevious by mutableStateOf(true)
        showOverlay(hasPrevious = { hasPrevious })
        composeTestRule.onNodeWithTag("player_previous_episode_button").requestFocus()
        composeTestRule.onNodeWithTag("player_previous_episode_button").assertIsFocused()

        hasPrevious = false
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_bottom_play_pause_button").assertIsFocused()
    }

    @Test
    fun whenTheFocusedNextEpisodeDisappearsFocusMovesToTheCentrePlayPause() {
        var next by mutableStateOf<String?>("Dark - S1E3 Past and Present")
        showOverlay(nextTitle = { next })
        composeTestRule.onNodeWithTag("player_next_episode_button").requestFocus()
        composeTestRule.onNodeWithTag("player_next_episode_button").assertIsFocused()

        next = null
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsFocused()
    }

    @Test
    fun playerExitsSurviveTheResultIntent() {
        val play = PlayerExit.PlayEpisode("e1", "Dark - S1E1 Secrets")
        assertEquals(play, PlayerExit.from(play.toIntent()))
        val details = PlayerExit.OpenDetails("e2")
        assertEquals(details, PlayerExit.from(details.toIntent()))
        assertNull("plain Back", PlayerExit.from(null))
        assertNull("no exit extras", PlayerExit.from(Intent()))
    }

    @Test
    fun detailsOpensTheFilmsPageOrTheSeriesPage() {
        val film = MediaItemEntity("film", MediaType.MOVIE, "Alien", parentFolderId = "films")
        val episode = MediaItemEntity("e1", MediaType.EPISODE, "Secrets", parentFolderId = "s1", seriesId = "dark")
        val looseEpisode = MediaItemEntity("e9", MediaType.EPISODE, "Loose", parentFolderId = "s1")
        assertEquals(Screen.MovieDetails("film"), PlayerExit.detailsScreen(film))
        assertEquals(Screen.SeriesDetails("dark"), PlayerExit.detailsScreen(episode))
        assertNull("an episode without a series has no page", PlayerExit.detailsScreen(looseEpisode))
        assertNull("deleted meanwhile", PlayerExit.detailsScreen(null))
    }
}
