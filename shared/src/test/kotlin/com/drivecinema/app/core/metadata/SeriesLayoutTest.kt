package com.drivecinema.app.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task R.1: the crawlers' season/episode rule, now in one place ([SeriesLayout]). */
class SeriesLayoutTest {

    @Test
    fun seasonFolderGivesTheSeasonAndTheFileTheNumber() {
        val key = SeriesLayout.episodeKey("Season 02", "Show S05E03 Pilot.mkv")
        assertEquals(2 to 3, key.pair)
        assertEquals("the file's own season is kept for the folder check", 5, key.fileSeason)
        assertEquals("Pilot", key.episodeTitle)
        assertFalse(key.seasonFolderWithoutNumber)
    }

    @Test
    fun fileInTheShowFolderGivesBoth() {
        assertEquals(2 to 3, SeriesLayout.episodeKey(null, "Show S02E03.mkv").pair)
        assertEquals("fansub: season 1, running number", 1 to 1171, SeriesLayout.episodeKey(null, "One Piece - 1171.mkv").pair)
        assertEquals("a bare number has no show name: season 1", 1 to 4, SeriesLayout.episodeKey(null, "04 Title.mkv").pair)
    }

    @Test
    fun folderWithoutANumberIsSeasonOneAndSaysSo() {
        val key = SeriesLayout.episodeKey("Specials", "Show S00E01.mkv")
        assertEquals(1 to 1, key.pair)
        assertTrue(key.seasonFolderWithoutNumber)
        assertEquals(0 to 1, SeriesLayout.episodeKey("Season 00", "Show S00E01.mkv").pair)
    }

    @Test
    fun nameWithoutANumberIsEpisodeOne() {
        val key = SeriesLayout.episodeKey("Season 01", "Behind the scenes.mkv")
        assertEquals(1 to 1, key.pair)
        assertEquals(EpisodeNamePattern.NONE, key.pattern)
        assertNull(key.fileSeason)
    }
}
