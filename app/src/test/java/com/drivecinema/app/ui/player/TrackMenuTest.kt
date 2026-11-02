package com.drivecinema.app.ui.player

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import com.drivecinema.app.core.player.TrackLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.compose.ui.test.requestFocus
import com.drivecinema.app.ui.design.assertFocusFitsVertically
import com.drivecinema.app.ui.design.DcTokens

/**
 * Task P.8: the player's audio and subtitle panels (TV at 960×540 dp): one check, on the track that plays; the
 * remote starts on it; OK picks and closes; Back closes and gives the focus back to the button; under half the width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w960dp-h540dp")
class TrackMenuTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private fun audioGroup(language: String, channels: Int, selected: Boolean, supported: Boolean = true): Tracks.Group {
        val format = Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AC3).setLanguage(language)
            .setChannelCount(channels).setSampleRate(48000).build()
        val support = if (supported) C.FORMAT_HANDLED else C.FORMAT_UNSUPPORTED_TYPE
        return Tracks.Group(TrackGroup("audio-$language", format), false, intArrayOf(support), booleanArrayOf(selected))
    }

    /** As in the user's file: an MKV, each audio track in its own group (both are track 0); English plays. */
    private val mkvTracks = Tracks(listOf(audioGroup("hu", 2, selected = false), audioGroup("en", 6, selected = true)))
    private val mkvAudio = AudioTrackHelper.extractAudioTracks(mkvTracks)

    private val subtitles = SubtitleTrackHelper.extractSubtitleTracks(
        Tracks(
            listOf(
                Tracks.Group(
                    TrackGroup(
                        "subs",
                        Format.Builder().setId("1").setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("hu").build(),
                        Format.Builder().setId("2").setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage("en").build()
                    ),
                    false, intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(false, true)
                )
            )
        )
    )

    @Test
    fun audioPanelChecksOnlyThePlayingTrack() {
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = true, onTrackSelected = {}, onClose = {}) }

        rule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
        rule.onNodeWithTag("track_choice_1").assertIsSelected()
    }

    @Test
    fun audioRowsShowTheLanguageThenTheFormat() {
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = true, onTrackSelected = {}, onClose = {}) }

        rule.onNodeWithText("Hungarian · Magyar").assertExists()
        rule.onNodeWithText("AC-3 (Dolby Digital) · Stereo").assertExists()
        rule.onNodeWithText("English").assertExists()
        rule.onNodeWithText("AC-3 (Dolby Digital) · 5.1").assertExists()
    }

    @Test
    fun tvRemoteStartsOnThePlayingTrack() {
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = true, onTrackSelected = {}, onClose = {}) }
        rule.waitForIdle()

        rule.onNodeWithTag("track_choice_1").assertIsFocused()
    }

    @Test
    fun phoneDoesNotFocusARow() {
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = false, onTrackSelected = {}, onClose = {}) }
        rule.waitForIdle()

        rule.onNodeWithTag("track_choice_0").assertIsNotFocused()
        rule.onNodeWithTag("track_choice_1").assertIsNotFocused()
    }

    @Test
    fun choosingATrackSelectsItAndCloses() {
        var chosen: AudioTrackInfo? = null
        var closed = 0
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = true, onTrackSelected = { chosen = it }, onClose = { closed++ }) }

        rule.onNodeWithTag("track_choice_0").performClick()

        assertEquals("hu", chosen?.language)
        assertEquals(1, closed)
    }

    @Test
    fun aTapOnTheScrimCloses() {
        var closed = 0
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = false, onTrackSelected = {}, onClose = { closed++ }) }

        rule.onNodeWithTag("track_panel_scrim").performClick() // the middle of the screen, left of the panel

        assertEquals(1, closed)
    }

    @Test
    fun aTrackTheDeviceCannotPlayIsDisabled() {
        val audio = AudioTrackHelper.extractAudioTracks(
            Tracks(listOf(audioGroup("en", 6, selected = true), audioGroup("de", 8, selected = false, supported = false)))
        )
        rule.setContent { AudioTrackPanel(audio, isTv = true, onTrackSelected = {}, onClose = {}) }

        rule.onNodeWithTag("track_choice_1").assertIsNotEnabled()
        rule.onNodeWithText("AC-3 (Dolby Digital) · 7.1 · ${TrackChoices.UNSUPPORTED}").assertExists()
    }

    @Test
    fun panelCoversLessThanHalfTheTvScreen() {
        rule.setContent { AudioTrackPanel(mkvAudio, isTv = true, onTrackSelected = {}, onClose = {}) }
        rule.waitForIdle()

        val panel = rule.onNodeWithTag("track_panel", useUnmergedTree = true).getBoundsInRoot()
        assertTrue("panel ${panel.right - panel.left} wide", panel.right - panel.left < 480.dp)
        assertEquals(960.dp, panel.right)
    }

    @Test
    fun subtitlesStartWithOffAndCheckTheShownTrack() {
        rule.setContent { SubtitleTrackPanel(subtitles, isOff = false, isTv = true, onTrackSelected = {}, onOff = {}, onClose = {}) }
        rule.waitForIdle()

        rule.onNodeWithTag("track_choice_0").assertExists()
        rule.onNodeWithText(TrackLabels.OFF).assertExists()
        rule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
        rule.onNodeWithTag("track_choice_2").assertIsSelected().assertIsFocused()
    }

    @Test
    fun offIsCheckedAndChosenWhileSubtitlesAreOff() {
        var off = 0
        var chosen: SubtitleTrackInfo? = null
        rule.setContent { SubtitleTrackPanel(subtitles, isOff = true, isTv = true, onTrackSelected = { chosen = it }, onOff = { off++ }, onClose = {}) }
        rule.waitForIdle()

        rule.onAllNodesWithContentDescription("Selected").assertCountEquals(1)
        rule.onNodeWithTag("track_choice_0").assertIsSelected().assertIsFocused().performClick()
        assertEquals(1, off)
        rule.onNodeWithTag("track_choice_1").performClick()
        assertEquals("hu", chosen?.language)
    }

    @Test
    fun focusedRowsAreNotClippedAtTheListsEnds() {
        // TV photos, 2026-10-29: the focused first row's ring was cut off at the top, the last row's at the bottom.
        // Twelve subtitle languages, so the list also scrolls.
        val many = SubtitleTrackHelper.extractSubtitleTracks(
            Tracks(listOf(Tracks.Group(
                TrackGroup("subs", *listOf("hu", "en", "de", "fr", "es", "it", "pl", "cs", "sk", "ro", "nl", "sv")
                    .mapIndexed { i, lang -> Format.Builder().setId("$i").setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage(lang).build() }
                    .toTypedArray()),
                false, IntArray(12) { C.FORMAT_HANDLED }, BooleanArray(12)
            )))
        )
        rule.setContent { SubtitleTrackPanel(many, isOff = true, isTv = true, onTrackSelected = {}, onOff = {}, onClose = {}) }
        rule.waitForIdle()

        for (index in (0..12) + (11 downTo 0)) {
            rule.onNodeWithTag("track_choice_$index").requestFocus()
            rule.waitForIdle()
            rule.onNodeWithTag("track_choice_$index")
                .assertFocusFitsVertically(rule.onNodeWithTag("track_panel_list", useUnmergedTree = true), scale = DcTokens.Focus.ROW_SCALE, what = "row $index")
        }
    }

    /** The player view with a fake player whose file has the two MKV audio tracks. */
    private class FakePlayer(private val tracks: Tracks) : SimpleBasePlayer(Looper.getMainLooper()) {
        override fun getState(): State = State.Builder()
            // No video surface: the test has no picture to show.
            .setAvailableCommands(
                Player.Commands.Builder()
                    .addAll(Player.COMMAND_GET_TRACKS, Player.COMMAND_GET_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_TIMELINE)
                    .build()
            )
            .setPlaylist(listOf(MediaItemData.Builder("item").setTracks(tracks).build()))
            .setPlaybackState(Player.STATE_READY)
            .build()
    }

    @Test
    fun backClosesThePanelAndTheAudioButtonGetsTheFocus() {
        val panel = mutableStateOf<TrackPanelKind?>(null)
        rule.setContent {
            MinimalPlayerView(player = FakePlayer(mkvTracks), title = "Dune", onBackPressed = {}, isTv = true, trackPanel = panel)
        }

        rule.onNodeWithTag("player_audio_button").performClick()
        rule.waitForIdle()
        assertEquals(TrackPanelKind.AUDIO, panel.value)
        rule.onAllNodesWithTag("playback_overlay_container").assertCountEquals(0)
        rule.onNodeWithTag("track_choice_1").assertIsFocused()

        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertNull(panel.value)
        rule.onAllNodesWithTag("track_panel", useUnmergedTree = true).assertCountEquals(0)
        rule.onNodeWithTag("player_audio_button").assertIsFocused()
    }
}
