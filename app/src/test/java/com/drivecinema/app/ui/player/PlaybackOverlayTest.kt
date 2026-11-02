package com.drivecinema.app.ui.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun testPlaybackOverlayRendersTitleAndControls() {
        var playPauseClicked = false
        var seekRelativeDelta = 0L

        composeTestRule.setContent {
            PlaybackOverlay(
                title = "Interstellar",
                isPlaying = true,
                currentPositionMs = 3600000L,
                durationMs = 7200000L,
                bufferedPositionMs = 5000000L,
                audioTracks = emptyList(),
                onPlayPauseClick = { playPauseClicked = true },
                onSeekRelative = { delta -> seekRelativeDelta = delta },
                onSeekTo = {},
                onAudioTrackClick = {},
                onBackPressed = {}
            )
        }

        composeTestRule.onNodeWithTag("player_title").assertIsDisplayed()
        composeTestRule.onNodeWithText("Interstellar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_play_pause_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_rewind_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_forward_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_timeline_scrubber").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_time_current").assertTextEquals("1:00:00")
        composeTestRule.onNodeWithTag("player_time_duration").assertTextEquals("2:00:00")
    }

    @Test
    fun testPlaybackOverlayPlayPauseAndSeekClicks() {
        var playPauseClicked = false
        var seekRelativeDelta = 0L

        composeTestRule.setContent {
            PlaybackOverlay(
                title = "Blade Runner 2049",
                isPlaying = false,
                currentPositionMs = 120000L,
                durationMs = 9000000L,
                bufferedPositionMs = 200000L,
                audioTracks = emptyList(),
                onPlayPauseClick = { playPauseClicked = true },
                onSeekRelative = { delta -> seekRelativeDelta = delta },
                onSeekTo = {},
                onAudioTrackClick = {},
                onBackPressed = {}
            )
        }

        composeTestRule.onNodeWithTag("player_play_pause_button").performClick()
        assertTrue(playPauseClicked)

        composeTestRule.onNodeWithTag("player_rewind_button").performClick()
        assertEquals(-10000L, seekRelativeDelta)

        composeTestRule.onNodeWithTag("player_forward_button").performClick()
        assertEquals(10000L, seekRelativeDelta)
    }

    @Test
    fun testPlaybackOverlayScrubberDpadNavigation() {
        var seekRelativeDelta = 0L

        composeTestRule.setContent {
            PlaybackOverlay(
                title = "Oppenheimer",
                isPlaying = true,
                currentPositionMs = 600000L,
                durationMs = 10800000L,
                bufferedPositionMs = 1200000L,
                audioTracks = emptyList(),
                onPlayPauseClick = {},
                onSeekRelative = { delta -> seekRelativeDelta = delta },
                onSeekTo = {},
                onAudioTrackClick = {},
                onBackPressed = {}
            )
        }

        val scrubberNode = composeTestRule.onNodeWithTag("player_timeline_scrubber")
        scrubberNode.requestFocus()

        // Press Left arrow
        scrubberNode.performKeyPress(
            androidx.compose.ui.input.key.KeyEvent(
                NativeKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT)
            )
        )
        assertEquals(-10000L, seekRelativeDelta)

        // Press Right arrow
        scrubberNode.performKeyPress(
            androidx.compose.ui.input.key.KeyEvent(
                NativeKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT)
            )
        )
        assertEquals(10000L, seekRelativeDelta)
    }

    @Test
    fun testPlaybackOverlayAudioButtonVisibility() {
        val tracks = listOf(
            AudioTrackInfo(trackIndex = 0, label = "English", language = "eng", isSupported = true),
            AudioTrackInfo(trackIndex = 1, label = "Hungarian", language = "hun", isSupported = true)
        )

        var audioClicked = false

        composeTestRule.setContent {
            PlaybackOverlay(
                title = "Arrival",
                isPlaying = true,
                currentPositionMs = 0L,
                durationMs = 6000000L,
                bufferedPositionMs = 1000000L,
                audioTracks = tracks,
                onPlayPauseClick = {},
                onSeekRelative = {},
                onSeekTo = {},
                onAudioTrackClick = { audioClicked = true },
                onBackPressed = {}
            )
        }

        val audioButton = composeTestRule.onNodeWithTag("player_audio_button")
        audioButton.assertIsDisplayed()
        audioButton.performClick()
        assertTrue(audioClicked)
    }

    @Test
    fun testPlaybackOverlayEndedState() {
        var returnClicked = false

        composeTestRule.setContent {
            PlaybackOverlay(
                title = "The Dark Knight",
                isPlaying = false,
                currentPositionMs = 9000000L,
                durationMs = 9000000L,
                bufferedPositionMs = 9000000L,
                audioTracks = emptyList(),
                isEnded = true,
                onPlayPauseClick = {},
                onSeekRelative = {},
                onSeekTo = {},
                onAudioTrackClick = {},
                onBackPressed = { returnClicked = true }
            )
        }

        composeTestRule.onNodeWithText("Video Finished").assertIsDisplayed()
        // Without a catalog item there is no details page to open, so the card's button just goes back.
        composeTestRule.onNodeWithText("Back").performClick()
        assertTrue(returnClicked)
    }

    /**
     * Report 2026-10-26: on the TV, "Next episode" and "Return to Details" on the end card did nothing. The focused
     * play/pause button leaves the screen when the video ends, so nothing had focus and OK reached no control.
     * The end card now takes the focus: Next episode when there is one, else Details (Back without an item).
     */
    @Test
    fun endCardTakesTheRemoteFocusSoOkPlaysTheNextEpisode() {
        var nextClicked = 0
        composeTestRule.setContent {
            PlaybackOverlay(
                title = "S01E01",
                isPlaying = false,
                currentPositionMs = 1_260_000L,
                durationMs = 1_260_000L,
                audioTracks = emptyList(),
                nextEpisodeTitle = "S01E02",
                isEnded = true,
                onPlayPauseClick = {},
                onSeekRelative = {},
                onSeekTo = {},
                onAudioTrackClick = {},
                onNextEpisodeClick = { nextClicked++ },
                onBackPressed = {}
            )
        }

        val next = composeTestRule.onNodeWithTag("player_next_episode_button")
        next.assertIsFocused()
        next.performKeyPress(
            androidx.compose.ui.input.key.KeyEvent(NativeKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        )
        assertEquals(1, nextClicked)
    }

    @Test
    fun endCardWithoutANextEpisodeFocusesReturnToDetails() {
        var returnClicked = 0
        composeTestRule.setContent {
            PlaybackOverlay(
                title = "The Dark Knight",
                isPlaying = false,
                currentPositionMs = 9_000_000L,
                durationMs = 9_000_000L,
                audioTracks = emptyList(),
                isEnded = true,
                onPlayPauseClick = {},
                onSeekRelative = {},
                onSeekTo = {},
                onAudioTrackClick = {},
                onBackPressed = { returnClicked++ }
            )
        }

        val back = composeTestRule.onNodeWithTag("player_return_to_details_button")
        back.assertIsFocused()
        back.performKeyPress(
            androidx.compose.ui.input.key.KeyEvent(NativeKeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER))
        )
        assertEquals(1, returnClicked)
    }
}
