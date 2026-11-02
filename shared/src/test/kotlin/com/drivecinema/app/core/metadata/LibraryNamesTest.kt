package com.drivecinema.app.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task R.1: the names the naming helper suggests (design §4.1, §4.2). */
class LibraryNamesTest {

    @Test
    fun charactersDriveAndWindowsRefuseAreRemoved() {
        assertEquals("Bleach Thousand-Year Blood War", LibraryNames.clean("Bleach: Thousand-Year Blood War"))
        assertEquals("Face Off", LibraryNames.clean("Face/Off"))
        assertEquals("What If", LibraryNames.clean("What If...?"))
        assertEquals("Grey's Anatomy & Co", LibraryNames.clean("Grey's  Anatomy & Co"))
        assertEquals("AB", LibraryNames.clean("A\u0007B*<>|\""))
    }

    @Test
    fun folderAndFileNames() {
        assertEquals("Breaking Bad (2008)", LibraryNames.seriesFolder("Breaking Bad", 2008, animeTag = false))
        assertEquals("One Piece (Anime) (1999)", LibraryNames.seriesFolder("One Piece", 1999, animeTag = true))
        assertEquals("Season 01", LibraryNames.seasonFolder(1))
        assertEquals("Season 00", LibraryNames.seasonFolder(0))
        assertEquals("Season 120", LibraryNames.seasonFolder(120))
        assertEquals("Breaking Bad S01E01.mkv", LibraryNames.episodeFile("Breaking Bad", 1, 1, "mkv"))
        assertEquals("One Piece S22E1086.mp4", LibraryNames.episodeFile("One Piece", 22, 1086, ".mp4"))
        assertEquals("One Piece - 1086.mkv", LibraryNames.fansubFile("One Piece", 1086, "mkv"))
        assertEquals("Ted (2012).mkv", LibraryNames.filmFile("Ted", 2012, "mkv"))
    }

    @Test
    fun arcTitleNamesTheShowOnce() {
        assertEquals("Bleach Thousand-Year Blood War", LibraryNames.arcTitle("Bleach", "Thousand-Year Blood War"))
        assertEquals("Bleach Thousand-Year Blood War", LibraryNames.arcTitle("Bleach", "Bleach: Thousand-Year Blood War"))
    }

    @Test
    fun extension() {
        assertEquals("mkv", LibraryNames.extensionOf("Show S01E01.mkv"))
        assertNull(LibraryNames.extensionOf("no extension"))
    }

    private fun result(id: Int, name: String, animated: Boolean, original: String = name) =
        TmdbSeriesResult(TmdbMetadata(id, name, null, null, null, null), original, animated)

    @Test
    fun animeTagOnlyBesideASameNameLiveActionShow() {
        val anime = result(37854, "One Piece", animated = true)
        val live = result(111110, "ONE PIECE", animated = false)
        assertTrue(LibraryNames.needsAnimeTag(anime, listOf(live, anime)))
        assertFalse("nothing else with that name", LibraryNames.needsAnimeTag(anime, listOf(anime)))
        assertFalse("live action never gets it", LibraryNames.needsAnimeTag(live, listOf(live, anime)))
        assertFalse(
            "an empty original name matches nothing",
            LibraryNames.needsAnimeTag(result(1, "A", animated = true, original = ""), listOf(result(2, "B", false, original = "")))
        )
    }
}
