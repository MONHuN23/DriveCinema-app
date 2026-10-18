package com.drivecinema.app.ui.setup

import com.drivecinema.app.core.metadata.MediaFilenameParser
import com.drivecinema.app.core.sync.FolderQueueCrawler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Keeps the in-app setup guide honest: every naming promise in it is checked against the real parser. */
class SetupGuideContentTest {

    private val examples = SetupGuideContent.sections.flatMap { it.examples }

    @Test
    fun guideHasNamingExamplesForFilmsAndEpisodes() {
        assertTrue(examples.any { it is NamingExample.Movie })
        assertTrue(examples.any { it is NamingExample.Episode })
    }

    @Test
    fun everyFilmExampleIsParsedExactlyAsTheGuideSays() {
        examples.filterIsInstance<NamingExample.Movie>().forEach { example ->
            val parsed = MediaFilenameParser.parseMovie(example.fileName)
            assertEquals("Title for ${example.fileName}", example.title, parsed.cleanTitle)
            assertEquals("Year for ${example.fileName}", example.year, parsed.year)
        }
    }

    @Test
    fun everyEpisodeExampleIsParsedExactlyAsTheGuideSays() {
        examples.filterIsInstance<NamingExample.Episode>().forEach { example ->
            val parsed = MediaFilenameParser.parseEpisode(example.fileName)
            assertEquals("Season for ${example.fileName}", example.season, parsed.season)
            assertEquals("Episode for ${example.fileName}", example.episode, parsed.episode)
        }
    }

    @Test
    fun everyExampleAndTreeFileIsRecognisedAsAVideo() {
        val treeFiles = SetupGuideContent.FOLDER_TREE.lines()
            .map { it.substringBefore("←").trim().trimStart('│', '├', '└', '─', ' ') }
            .filter { it.contains('.') && !it.endsWith("/") }
        assertTrue(treeFiles.isNotEmpty())
        (examples.map { it.fileName } + treeFiles).forEach { name ->
            assertTrue("$name must be a recognised video file", FolderQueueCrawler.isVideo(null, name))
        }
    }

    @Test
    fun folderTreeEpisodesParseToTheirSeasonAndEpisode() {
        val s01e02 = MediaFilenameParser.parseEpisode("Breaking Bad - S01E02.mkv")
        assertEquals(1, s01e02.season)
        assertEquals(2, s01e02.episode)
        val movie = MediaFilenameParser.parseMovie("Inception.2010.1080p.mkv")
        assertEquals("Inception", movie.cleanTitle)
        assertEquals(2010, movie.year)
    }

    @Test
    fun guideNamesTheFoldersTheAppActuallyLooksFor() {
        val text = SetupGuideContent.sections.joinToString(" ") { s -> (listOfNotNull(s.intro) + s.bullets).joinToString(" ") }
        assertTrue(text.contains("\"Films\""))
        assertTrue(text.contains("\"Series\""))
        assertTrue(text.contains("\"Streaming\""))
        assertTrue(SetupGuideContent.FOLDER_TREE.contains("Films/"))
        assertTrue(SetupGuideContent.FOLDER_TREE.contains("Series/"))
        assertFalse("Subtitles are out of scope; the guide must not promise them", text.contains("subtitle file", ignoreCase = true))
    }

    @Test
    fun guideCarriesTheExactTmdbNotice() {
        // TMDB's API terms require this wording in an About/Credits section (Milestone J.3).
        val credits = SetupGuideContent.sections.single { it.title.contains("credits", ignoreCase = true) }
        assertEquals("This product uses the TMDB API but is not endorsed or certified by TMDB.", credits.intro)
    }
}
