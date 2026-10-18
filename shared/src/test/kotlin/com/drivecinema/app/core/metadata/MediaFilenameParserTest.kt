package com.drivecinema.app.core.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaFilenameParserTest {

    @Test
    fun testParseStandardMovieFilename() {
        val result = MediaFilenameParser.parseMovie("Inception.2010.1080p.BluRay.x264.mkv")
        assertEquals("Inception", result.cleanTitle)
        assertEquals(2010, result.year)
    }

    @Test
    fun testParseMovieWithParenthesizedYear() {
        val result = MediaFilenameParser.parseMovie("Dune Part Two (2024).mkv")
        assertEquals("Dune Part Two", result.cleanTitle)
        assertEquals(2024, result.year)
    }

    @Test
    fun testParseMovieWithBracketedYear() {
        val result = MediaFilenameParser.parseMovie("The.Matrix.[1999].mkv")
        assertEquals("The Matrix", result.cleanTitle)
        assertEquals(1999, result.year)
    }

    @Test
    fun testParseStandardEpisodeFilename() {
        val result = MediaFilenameParser.parseEpisode("Stranger.Things.S04E07.1080p.mkv")
        assertEquals("Stranger Things", result.cleanShowName)
        assertEquals(4, result.season)
        assertEquals(7, result.episode)
    }

    @Test
    fun testParseEpisodeWithLowercaseAndDash() {
        val result = MediaFilenameParser.parseEpisode("Breaking-Bad-s01e01.mkv")
        assertEquals("Breaking Bad", result.cleanShowName)
        assertEquals(1, result.season)
        assertEquals(1, result.episode)
    }

    @Test
    fun testParseNonStandardFilenameGracefulFallback() {
        val result = MediaFilenameParser.parseMovie("Special_Movie_Feature_Cut.mkv")
        assertEquals("Special Movie Feature Cut", result.cleanTitle)
        assertNull(result.year)
    }

    @Test
    fun testParseDunePartTwoWithDots() {
        val result = MediaFilenameParser.parseMovie("Dune.Part.Two.2024.1080p.mkv")
        assertEquals("Dune Part Two", result.cleanTitle)
        assertEquals(2024, result.year)
    }

    @Test
    fun testParseBreakingBadTrailingDots() {
        val result = MediaFilenameParser.parseEpisode("Breaking.Bad.S01E02....mkv")
        assertEquals("Breaking Bad", result.cleanShowName)
        assertEquals(1, result.season)
        assertEquals(2, result.episode)
    }

    @Test
    fun testParseEpisodeWithTitle() {
        val result = MediaFilenameParser.parseEpisode("Some Show - S02E05 - Episode Name.mkv")
        assertEquals("Some Show", result.cleanShowName)
        assertEquals(2, result.season)
        assertEquals(5, result.episode)
        assertEquals("Episode Name", result.episodeTitle)
    }

    @Test
    fun testParseBareEpisodeWithTitle() {
        val result = MediaFilenameParser.parseEpisode("S01E03 - Cats in the Bag.mkv")
        assertEquals(1, result.season)
        assertEquals(3, result.episode)
        assertEquals("Cats in the Bag", result.episodeTitle)
    }

    @Test
    fun testParseBareNumberedEpisode() {
        val result = MediaFilenameParser.parseEpisode("02 - Pilot Episode.mkv")
        assertEquals(1, result.season)
        assertEquals(2, result.episode)
        assertEquals("Pilot Episode", result.episodeTitle)
    }

    @Test
    fun sceneEpisodeNamesHaveNoTagTitle() {
        // Regression: "Rick.and.Morty.The.Anime.S01E01.720p-VARYG.mkv" showed "720p VARYG" as the episode title.
        val result = MediaFilenameParser.parseEpisode("Rick.and.Morty.The.Anime.S01E01.720p-VARYG.mkv")
        assertEquals("Rick and Morty The Anime", result.cleanShowName)
        assertEquals(1, result.season)
        assertEquals(1, result.episode)
        assertEquals(null, result.episodeTitle)
    }

    @Test
    fun sceneEpisodeKeepsTheRealTitleBeforeTheTags() {
        val result = MediaFilenameParser.parseEpisode("The.Office.S02E01.The.Dundies.1080p.WEB-DL.x264-GRP.mkv")
        assertEquals("The Dundies", result.episodeTitle)
        assertEquals(2, result.season)
    }

    @Test
    fun sceneFilmWithoutYearDropsTags() {
        assertEquals("Some Film", MediaFilenameParser.parseMovie("Some.Film.1080p.BluRay.x265.10bit.mkv").cleanTitle)
        assertEquals("Heat", MediaFilenameParser.parseMovie("Heat.1995.1080p.mkv").cleanTitle)
    }

    @Test
    fun ordinaryTitlesAreNotCut() {
        assertEquals("Ozymandias", MediaFilenameParser.parseEpisode("Breaking Bad - S05E14 - Ozymandias.mkv").episodeTitle)
        assertEquals("Pilot Episode", MediaFilenameParser.parseEpisode("01 - Pilot Episode.mkv").episodeTitle)
    }

    @Test
    fun titlesThatContainTagWordsSurvive() {
        assertEquals("Charlottes Web", MediaFilenameParser.parseMovie("Charlottes.Web.mkv").cleanTitle)
        assertEquals("The Web", MediaFilenameParser.parseEpisode("Show.S01E02.The.Web.mkv").episodeTitle)
        assertEquals("Movie Name", MediaFilenameParser.parseMovie("Movie.Name.WEB-DL.x264-GRP.mkv").cleanTitle)
    }
}
