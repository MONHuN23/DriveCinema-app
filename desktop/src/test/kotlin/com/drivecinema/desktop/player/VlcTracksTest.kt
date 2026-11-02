package com.drivecinema.desktop.player

import org.junit.Assert.assertEquals
import org.junit.Test

/** Task K.1: VLC's subtitle track descriptions become readable menu entries; VLC's "Disable" is our "Off". */
class VlcTracksTest {

    @Test
    fun vlcDescriptionsBecomeReadableLabels() {
        assertEquals("Hungarian", VlcTracks.label(1, "Track 1 - [Hungarian]"))
        assertEquals("English [SDH]", VlcTracks.label(2, "Track 2 - English [SDH]"))
        assertEquals("Commentary", VlcTracks.label(3, "Subtitles 3 - Commentary"))
        assertEquals("Film.hu.srt", VlcTracks.label(4, "Film.hu.srt"))
        assertEquals("Track 5", VlcTracks.label(5, null))
        assertEquals("Track 6", VlcTracks.label(6, "Track 6 - "))
    }

    @Test
    fun vlcsOwnDisableEntryIsLeftOutBecauseTheMenuHasOff() {
        val options = VlcTracks.subtitleOptions(listOf(-1 to "Disable", 2 to "Track 1 - [English]", 3 to "Track 2 - [Hungarian]"))
        assertEquals(listOf(AudioTrackOption(2, "English"), AudioTrackOption(3, "Hungarian · Magyar")), options)
        assertEquals(-1, VlcTracks.OFF)
    }

    @Test
    fun audioEntriesShowTheLanguageThenTheFormatFromVlcsTrackInfo() {
        // P.8: before, the audio menu showed VLC's raw description ("Track 1 - [Hungarian]").
        val options = VlcTracks.audioOptions(
            listOf(-1 to "Disable", 1 to "Track 1 - [Hungarian]", 2 to "Track 2 - [English]", 3 to "Track 3"),
            listOf(VlcTrackDetails(1, "hun", "a52 ", 2), VlcTrackDetails(2, "eng", "a52 ", 6))
        )
        assertEquals(
            listOf(
                AudioTrackOption(1, "Hungarian · Magyar", "AC-3 (Dolby Digital) · Stereo"),
                AudioTrackOption(2, "English", "AC-3 (Dolby Digital) · 5.1"),
                AudioTrackOption(3, "Track 3", null)
            ),
            options
        )
    }

    @Test
    fun subtitleEntriesAddTheTrackTitleAndFormat() {
        val options = VlcTracks.subtitleOptions(
            listOf(4 to "Track 1 - Commentary - [English]", 5 to "Track 2 - [Hungarian]"),
            listOf(VlcTrackDetails(4, "eng", "subt", description = "Commentary"), VlcTrackDetails(5, "hun", "ssa "))
        )
        assertEquals(
            listOf(AudioTrackOption(4, "English", "Commentary · SRT"), AudioTrackOption(5, "Hungarian · Magyar", "ASS")),
            options
        )
    }
}
