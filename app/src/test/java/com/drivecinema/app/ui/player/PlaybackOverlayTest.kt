package com.drivecinema.app.ui.player

import android.view.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.test.assertIsDisplayed
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
        composeTestRule.onNodeWithText("Return to Details").performClick()
        assertTrue(returnClicked)
    }
}
