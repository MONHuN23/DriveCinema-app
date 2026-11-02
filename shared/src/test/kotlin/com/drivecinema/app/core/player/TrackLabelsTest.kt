package com.drivecinema.app.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Task P.8: the track menus' words, the same for Media3 and VLC codes. */
class TrackLabelsTest {

    @Test
    fun languageShowsTheEnglishNameThenTheNativeOne() {
        assertEquals("Hungarian · Magyar", TrackLabels.language("hu"))
        assertEquals("German · Deutsch", TrackLabels.language("de"))
        assertEquals("English", TrackLabels.language("en"))
    }

    @Test
    fun languageAcceptsVlcAndRegionCodes() {
        assertEquals("Hungarian · Magyar", TrackLabels.language("hun"))
        assertEquals("German · Deutsch", TrackLabels.language("ger"))
        assertEquals("German · Deutsch", TrackLabels.language("deu"))
        assertEquals("English", TrackLabels.language("eng"))
        assertEquals("English", TrackLabels.language("en-US"))
        assertEquals("Hungarian · Magyar", TrackLabels.language("Hungarian"))
    }

    @Test
    fun unknownLanguagesAreSaidPlainly() {
        assertEquals(TrackLabels.UNKNOWN_LANGUAGE, TrackLabels.language(null))
        assertEquals(TrackLabels.UNKNOWN_LANGUAGE, TrackLabels.language(" "))
        assertEquals(TrackLabels.UNKNOWN_LANGUAGE, TrackLabels.language("und"))
        assertEquals("QQX", TrackLabels.language("qqx"))
        assertEquals("Klingon commentary", TrackLabels.language("Klingon commentary"))
    }

    @Test
    fun audioFormatIsCodecThenChannels() {
        assertEquals("AC-3 (Dolby Digital) · 5.1", TrackLabels.audioFormat("audio/ac3", 6))
        assertEquals("AC-3 (Dolby Digital) · Stereo", TrackLabels.audioFormat("a52 ", 2))
        assertEquals("E-AC-3 (Dolby Digital Plus) · 7.1", TrackLabels.audioFormat("audio/eac3", 8))
        assertEquals("Dolby Atmos", TrackLabels.audioFormat("audio/eac3-joc", -1))
        assertEquals("AAC · Stereo", TrackLabels.audioFormat("mp4a", 2))
        assertEquals("CUSTOM", TrackLabels.audioFormat("audio/custom", 0))
        assertEquals("Mono", TrackLabels.audioFormat(null, 1))
        assertNull(TrackLabels.audioFormat(null, null))
    }

    @Test
    fun subtitleFormatsFromMimeTypesAndFourCcs() {
        assertEquals("SRT", TrackLabels.subtitleFormat("application/x-subrip"))
        assertEquals("SRT", TrackLabels.subtitleFormat("subt"))
        assertEquals("ASS", TrackLabels.subtitleFormat("ssa "))
        assertEquals("PGS", TrackLabels.subtitleFormat("application/pgs"))
        assertEquals("WebVTT", TrackLabels.subtitleFormat("text/vtt"))
        assertNull(TrackLabels.subtitleFormat("application/x-something"))
        assertNull(TrackLabels.subtitleFormat(null))
    }

    @Test
    fun subtitleDetailsAddOnlyWhatTheLanguageDoesNotSay() {
        assertEquals("Commentary · SRT", TrackLabels.subtitleDetails("en", "Commentary", false, false, "SRT"))
        assertNull(TrackLabels.subtitleDetails("en", "English", false, false, null))
        assertNull(TrackLabels.subtitleDetails("hu", "Magyar", false, false, null))
        assertEquals("Forced subs", TrackLabels.subtitleDetails("en", "Forced subs", true, false, null))
        assertEquals("Forced · SDH · PGS", TrackLabels.subtitleDetails("en", null, true, true, "PGS"))
        assertNull(TrackLabels.subtitleDetails("und", "Director's notes", false, false, null))
    }

    @Test
    fun subtitleTitleFallsBackToTheTrackTitleWithoutALanguage() {
        assertEquals("Hungarian · Magyar", TrackLabels.subtitleTitle("hu", "Magyar felirat"))
        assertEquals("Director's notes", TrackLabels.subtitleTitle("und", "Director's notes"))
        assertEquals(TrackLabels.UNKNOWN_LANGUAGE, TrackLabels.subtitleTitle(null, " "))
    }
}
