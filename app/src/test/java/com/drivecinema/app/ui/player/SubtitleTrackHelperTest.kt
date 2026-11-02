package com.drivecinema.app.ui.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Task K.1: subtitle tracks inside the file are listed with readable labels and can be chosen or switched off. */
@RunWith(RobolectricTestRunner::class)
class SubtitleTrackHelperTest {

    private fun text(id: String, language: String?, flags: Int = 0, role: Int = 0, label: String? = null) =
        Format.Builder().setId(id).setSampleMimeType(MimeTypes.APPLICATION_SUBRIP).setLanguage(language)
            .setSelectionFlags(flags).setRoleFlags(role).setLabel(label).build()

    private val subtitles = TrackGroup(
        "subs",
        text("1", "hu"),
        text("2", "en", flags = C.SELECTION_FLAG_FORCED),
        text("3", "en", role = C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND),
        text("4", "de")
    )
    private val audio = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("en").build())

    private val tracks = Tracks(
        listOf(
            Tracks.Group(audio, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true)),
            // German is in a format this device can't render: not offered.
            Tracks.Group(
                subtitles, false,
                intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED, C.FORMAT_HANDLED, C.FORMAT_UNSUPPORTED_SUBTYPE),
                booleanArrayOf(true, false, false, false)
            )
        )
    )

    @Test
    fun onlyPlayableSubtitleTracksAreListedWithReadableLabels() {
        val list = SubtitleTrackHelper.extractSubtitleTracks(tracks)
        // P.8: the language first, then what else there is to say (forced, SDH, the format).
        assertEquals(listOf("Hungarian · Magyar", "English", "English"), list.map { it.title })
        assertEquals(listOf("SRT", "Forced · SRT", "SDH · SRT"), list.map { it.details })
        assertEquals(listOf(true, false, false), list.map { it.isSelected })
        assertTrue(list[1].isForced)
    }

    @Test
    fun labelsUseTheTrackTitleOnlyWhenItAddsSomething() {
        val commentary = Tracks(listOf(Tracks.Group(TrackGroup(text("1", "en", label = "Commentary"), text("2", "und", label = "English")), false,
            intArrayOf(C.FORMAT_HANDLED, C.FORMAT_HANDLED), booleanArrayOf(false, false))))
        val list = SubtitleTrackHelper.extractSubtitleTracks(commentary)
        assertEquals(listOf("English", "English"), list.map { it.title })
        assertEquals(listOf("Commentary · SRT", "SRT"), list.map { it.details })
    }

    @Test
    fun choosingATrackOverridesTheTextTypeAndOffDisablesIt() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
        try {
            val english = SubtitleTrackHelper.extractSubtitleTracks(tracks)[2]
            SubtitleTrackHelper.select(player, english)
            val params = player.trackSelectionParameters
            assertFalse(C.TRACK_TYPE_TEXT in params.disabledTrackTypes)
            assertEquals(listOf(2), params.overrides[subtitles]!!.trackIndices)

            SubtitleTrackHelper.turnOff(player)
            assertTrue(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes)
            assertTrue(player.trackSelectionParameters.overrides.isEmpty())
            assertTrue(SubtitleTrackHelper.isOff(player, SubtitleTrackHelper.extractSubtitleTracks(tracks)))

            SubtitleTrackHelper.select(player, english)
            assertFalse("choosing a track after Off turns subtitles back on", C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes)
        } finally {
            player.release()
        }
    }

    @Test
    fun noTextTracksMeansNoSubtitleButton() {
        val audioOnly = Tracks(listOf(Tracks.Group(audio, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(true))))
        assertTrue(SubtitleTrackHelper.extractSubtitleTracks(audioOnly).isEmpty())
    }
}
