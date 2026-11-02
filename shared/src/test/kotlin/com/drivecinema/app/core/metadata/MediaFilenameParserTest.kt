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

    // F-43: every One Piece file was S01E01: the episode number allowed two digits, and fansub names have no season.

    @Test
    fun episodeNumbersOfThreeAndFourDigitsAreRead() {
        val scene = MediaFilenameParser.parseEpisode("one.piece.s24e1180.1080p.web.h264-senpai.mkv")
        assertEquals("one piece", scene.cleanShowName)
        assertEquals(24, scene.season)
        assertEquals(1180, scene.episode)
        assertEquals(105, MediaFilenameParser.parseEpisode("Naruto.S03E105.mkv").episode)
    }

    @Test
    fun fansubNamesGiveTheRunningNumberInSeasonOne() {
        val fansub = MediaFilenameParser.parseEpisode("[SubsPlease] One Piece - 1171 (1080p) [08CA0446].mkv")
        assertEquals("One Piece", fansub.cleanShowName)
        assertEquals(1, fansub.season)
        assertEquals(1171, fansub.episode)
        assertEquals(12, MediaFilenameParser.parseEpisode("[Group] Frieren - 12v2 [1080p].mkv").episode)
        assertEquals(7, MediaFilenameParser.parseEpisode("Some Show - 07.mkv").episode)
    }

    @Test
    fun aResolutionAfterTheDashIsNotAnEpisodeNumber() {
        assertEquals(1, MediaFilenameParser.parseEpisode("Some Show - 1080p.mkv").episode)
        assertEquals(5, MediaFilenameParser.parseEpisode("Some Show - S02E05 - Episode Name.mkv").episode)
        assertEquals(2, MediaFilenameParser.parseEpisode("Some Show - S02E05 - Episode Name.mkv").season)
    }

    // --- F-44: the last year-like number is the year ---

    @Test
    fun aTitleWithAYearLikeNumberKeepsIt() {
        val blade = MediaFilenameParser.parseMovie("Blade Runner 2049 (2017).mkv")
        assertEquals("Blade Runner 2049", blade.cleanTitle)
        assertEquals(2017, blade.year)
        val scene = MediaFilenameParser.parseMovie("Blade.Runner.2049.2017.2160p.UHD.mkv")
        assertEquals("Blade Runner 2049", scene.cleanTitle)
        assertEquals(2017, scene.year)
    }

    @Test
    fun titlesThatAreANumberStillGetTheirYear() {
        assertEquals(MovieParseResult("1917", 2019), MediaFilenameParser.parseMovie("1917 (2019).mkv"))
        assertEquals(MovieParseResult("2001 A Space Odyssey", 1968), MediaFilenameParser.parseMovie("2001 A Space Odyssey (1968).mkv"))
        assertEquals(MovieParseResult("Ted", 2024), MediaFilenameParser.parseMovie("Ted (2024)"))
    }

    @Test
    fun animeTagBeforeTheYearIsKeptAfterItIsLost() {
        assertEquals(MovieParseResult("One Piece Anime", 1999), MediaFilenameParser.parseMovie("One Piece (Anime) (1999)"))
        assertEquals(MovieParseResult("One Piece", 1999), MediaFilenameParser.parseMovie("One Piece (1999) (Anime)"))
    }

    // --- Task R.1: which pattern read the name ---

    @Test
    fun episodeResultSaysWhichPatternReadIt() {
        assertEquals(EpisodeNamePattern.SEASON_EPISODE, MediaFilenameParser.parseEpisode("Show S01E04.mkv").pattern)
        assertEquals(EpisodeNamePattern.RUNNING_NUMBER, MediaFilenameParser.parseEpisode("[SubsPlease] One Piece - 1171 (1080p).mkv").pattern)
        assertEquals(EpisodeNamePattern.BARE_NUMBER, MediaFilenameParser.parseEpisode("04 The Title.mkv").pattern)
        val none = MediaFilenameParser.parseEpisode("Behind the scenes.mkv")
        assertEquals(EpisodeNamePattern.NONE, none.pattern)
        assertEquals("read as E1 by default", 1, none.episode)
    }
}
