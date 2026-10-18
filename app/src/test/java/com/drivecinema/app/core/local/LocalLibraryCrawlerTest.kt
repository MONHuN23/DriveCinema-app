package com.drivecinema.app.core.local

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class LocalLibraryCrawlerTest {

    private val tree = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies")

    @Test
    fun followsTheSameLayoutRulesAsDrive() {
        val fake = FakeDocumentTree()
            .file("Root Film (2001).mp4")
            .file("Films/Dune Part Two (2024).mkv")
            .file("Films/Nested/Deeper/Inception.2010.1080p.mkv")
            .file("Films/notes.txt")
            .file("Series/Breaking Bad/Season 02/Breaking Bad - S02E05 - Title.mkv")
            .file("Series/Breaking Bad/Breaking Bad - S03E01.mkv")
            .file("Series/Breaking Bad/S04E02.mkv")
            .folder("Series/Empty Show")

        val library = LocalLibraryCrawler(fake).crawl(tree)

        assertEquals(
            setOf("Root Film (2001).mp4", "Dune Part Two (2024).mkv", "Inception.2010.1080p.mkv"),
            library.films.map { it.fileName }.toSet()
        )
        val show = library.shows.single { it.title == "Breaking Bad" }
        val byFile = show.episodes.associateBy { it.video.fileName }
        assertEquals(2 to 5, byFile.getValue("Breaking Bad - S02E05 - Title.mkv").let { it.season to it.episode })
        assertEquals("Season from the file name when it includes the show", 3 to 1,
            byFile.getValue("Breaking Bad - S03E01.mkv").let { it.season to it.episode })
        assertEquals("Without the show name a loose episode counts as season 1", 1 to 2,
            byFile.getValue("S04E02.mkv").let { it.season to it.episode })
        assertTrue(library.shows.any { it.title == "Empty Show" && it.episodes.isEmpty() })
        assertTrue(library.films.all { it.sourceKey.startsWith(tree.toString()) })
    }

    @Test(expected = IllegalStateException::class)
    fun anyListingFailureFailsTheWholeCrawl() {
        val fake = FakeDocumentTree().file("Films/Dune (2021).mkv").file("Films/Sub/A (2000).mkv")
        fake.failingFolders += "root:Films/Sub/"
        LocalLibraryCrawler(fake).crawl(tree)
    }

    /** Report F-14: same season-folder rule as the Drive crawler (same cases as shared SeasonFolderParsingTest). */
    @Test
    fun seasonComesOnlyFromASeasonMarker() {
        val cases = linkedMapOf(
            "Season 1" to 1, "Season 02" to 2, "season 10" to 10, "S3" to 3, "Show_S4" to 4, "Season5" to 5,
            "Show.S06.1080p" to 6, "S07E01-E10" to 7, "Season 8 (2019)" to 8,
            "Extras 2" to 1, "Bonus 2019" to 1, "Specials" to 1, "Behind the Scenes 3" to 1
        )
        val folders = cases.keys.toList()
        val fake = FakeDocumentTree()
        folders.forEachIndexed { i, folder -> fake.file("Series/Show/$folder/Episode 1 case$i.mkv") }
        val show = LocalLibraryCrawler(fake).crawl(tree).shows.single { it.title == "Show" }
        val seasonByFolder = show.episodes.associate { ep ->
            folders[Regex("""case(\d+)""").find(ep.video.fileName)!!.groupValues[1].toInt()] to ep.season
        }
        assertEquals(cases, folders.associateWith { seasonByFolder[it] })
    }
}
