package com.drivecinema.app.core.metadata

import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Task R.1: the folder check reads a series folder the way the sync does, names each problem, and suggests names the
 * sync reads back as the episode they are for (checked through [FakeTmdb.syncReads]).
 */
class FolderCheckTest {

    private val tmdb = FakeTmdb.library()
    private val today = LocalDate.parse("2026-11-01")

    @After
    fun tearDown() = tmdb.close()

    private fun listing(folder: String, vararg files: Pair<String?, String>) = SeriesFolderListing(
        seriesFolderId = "series",
        seriesFolderName = folder,
        files = files.mapIndexed { i, (season, name) ->
            SeriesFolderListing.ListedFile("f$i", name, folderId = season ?: "series", seasonFolderName = season)
        }
    )

    private suspend fun check(listing: SeriesFolderListing) = FolderCheck(tmdb.client).check(listing, today).getOrThrow()

    private fun FolderCheck.Report.result(name: String) = files.single { it.file.name == name }

    /** Every suggestion and missing-episode name reads back, together with the folder's matched files, as its episode. */
    private suspend fun assertSuggestionsReadBack(report: FolderCheck.Report) {
        val suggestions = report.files.mapNotNull {
            when (it) {
                is FileResult.NotRecognised -> it.suggestion
                is FileResult.Duplicate -> it.suggestion
                is FileResult.SeasonMismatch -> it.suggestion
                is FileResult.Matched -> null
            }
        } + report.missing.map { it.suggestion }
        assertTrue(suggestions.isNotEmpty())
        val matched = report.files.filterIsInstance<FileResult.Matched>().map { it.file.seasonFolderName to it.file.name }
        val read = tmdb.syncReads(report.listing.seriesFolderName, matched + suggestions.map { it.seasonFolder to it.fileName })!!
        for (s in suggestions) {
            val back = read.episodes[s.seasonFolder to s.fileName]
            assertEquals("${s.seasonFolder}/${s.fileName}", s.episode.seasonNumber to s.episode.episodeNumber,
                back?.let { it.seasonNumber to it.episodeNumber })
        }
    }

    @Test
    fun eachKindOfProblemWithItsSuggestion() = runBlocking {
        val report = check(listing(
            "Breaking Bad (2008)",
            "Season 01" to "Breaking Bad S01E01.mkv",
            "Season 01" to "Breaking Bad S01E02.mkv",
            "Season 01" to "Breaking.Bad.S01E02.720p.mkv",
            "Season 01" to "Breaking Bad S02E03.mkv",
            "Season 01" to "Behind the scenes.mkv",
            "Season 02" to "Breaking Bad S02E99.mkv"
        ))
        assertEquals(1396, report.show?.tmdbId)
        assertTrue(report.result("Breaking Bad S01E01.mkv") is FileResult.Matched)

        val duplicate = report.result("Breaking Bad S01E02.mkv") as FileResult.Duplicate
        assertEquals(listOf("Breaking.Bad.S01E02.720p.mkv"), duplicate.otherFiles.map { it.name })
        assertNull("both say S01E02: nothing better to suggest", duplicate.suggestion)

        val mismatch = report.result("Breaking Bad S02E03.mkv") as FileResult.SeasonMismatch
        assertEquals("read as the folder's season", 1 to 3, mismatch.readAs?.let { it.seasonNumber to it.episodeNumber })
        assertEquals(2, mismatch.fileSays)
        assertEquals("Season 02", mismatch.suggestion?.seasonFolder)
        assertEquals("Breaking Bad S02E03.mkv", mismatch.suggestion?.fileName)
        assertFalse("it has to move", mismatch.suggestion!!.inPlace(mismatch.file))

        val noNumber = report.result("Behind the scenes.mkv") as FileResult.NotRecognised
        assertEquals(FolderCheck.Reason.NO_EPISODE_NUMBER, noNumber.reason)

        val notOnTmdb = report.result("Breaking Bad S02E99.mkv") as FileResult.NotRecognised
        assertEquals(FolderCheck.Reason.NOT_ON_TMDB, notOnTmdb.reason)
        assertNull(notOnTmdb.suggestion)

        assertEquals(
            "season 1's other aired episodes; season 2 has no file the app reads into it",
            listOf(1 to 4, 1 to 5, 1 to 6, 1 to 7),
            report.missing.map { it.episode.seasonNumber to it.episode.episodeNumber }
        )
        assertEquals("Breaking Bad S01E04.mkv", report.missing.first().suggestion.fileName)
        assertTrue(report.foldersToCreate.isEmpty())
        assertSuggestionsReadBack(report)
    }

    @Test
    fun seasonFolderWithoutANumberAndFoldersToCreate() = runBlocking {
        val report = check(listing(
            "Breaking Bad (2008)",
            "Extras" to "Breaking Bad S01E09.mkv",
            "Season 01" to "Breaking Bad S01E01.mkv"
        ))
        val extras = report.result("Breaking Bad S01E09.mkv") as FileResult.NotRecognised
        assertEquals(FolderCheck.Reason.SEASON_FOLDER_WITHOUT_NUMBER, extras.reason)
        assertTrue(report.foldersToCreate.isEmpty())

        val flat = check(listing("Breaking Bad (2008)", null to "Breaking Bad S02E05.mkv"))
        assertTrue(flat.result("Breaking Bad S02E05.mkv") is FileResult.Matched)
        assertEquals(listOf("Season 02"), flat.foldersToCreate)
    }

    @Test
    fun fansubFolderGetsFansubSuggestions() = runBlocking {
        val report = check(listing(
            "One Piece (Anime)",
            "Season 01" to "One Piece - 1086.mkv",
            "Season 01" to "[SubsPlease] One Piece - 1087 (1080p) [08CA0446].mkv",
            "Season 01" to "One Piece - 1090.mkv"
        ))
        assertEquals("the anime, not the live-action show", 37854, report.show?.tmdbId)
        assertTrue(report.problems.isEmpty())
        val first = report.missing.first()
        assertEquals(22 to 1088, first.episode.seasonNumber to first.episode.episodeNumber)
        assertEquals("Season 01", first.suggestion.seasonFolder)
        assertEquals("One Piece - 1088.mkv", first.suggestion.fileName)
        assertEquals("all of season 22 but the three files", 70 - 3, report.missing.size)
        assertSuggestionsReadBack(report)
    }

    @Test
    fun arcFolderKeepsItsLayout() = runBlocking {
        val report = check(listing(
            "Bleach Thousand-Year Blood War (2022)",
            "Season 01" to "Bleach Thousand-Year Blood War S01E01.mkv",
            "Season 02" to "Bleach Thousand-Year Blood War S02E01.mkv"
        ))
        assertEquals(2, report.arc?.seasonNumber)
        assertTrue(report.problems.isEmpty())
        val episode15 = report.missing.first { it.episode.episodeNumber == 15 }.suggestion
        assertEquals("Season 02", episode15.seasonFolder)
        assertEquals("Bleach Thousand-Year Blood War S02E02.mkv", episode15.fileName)
        assertEquals("Bleach Thousand-Year Blood War S04E01.mkv", report.missing.first { it.episode.episodeNumber == 41 }.suggestion.fileName)
        assertEquals(listOf("Season 03", "Season 04"), report.foldersToCreate)
        assertEquals(50 - 2, report.missing.size)
        assertSuggestionsReadBack(report)
    }

    @Test
    fun notYetAiredEpisodesAreNotMissing() = runBlocking {
        val report = FolderCheck(tmdb.client).check(
            listing("Bleach Thousand-Year Blood War (2022)", "Season 01" to "Bleach Thousand-Year Blood War S01E01.mkv"),
            today = LocalDate.parse("2026-08-01")
        ).getOrThrow()
        assertEquals("part 4 started 2026-07-25: two episodes by 2026-08-01", 40 + 2 - 1, report.missing.size)
    }

    @Test
    fun folderTmdbDoesNotFind() = runBlocking {
        val report = check(listing("Nothing Like This (2001)", "Season 01" to "Nothing S01E01.mkv"))
        assertNull(report.show)
        assertEquals(FolderCheck.Reason.NOT_ON_TMDB, (report.files.single() as FileResult.NotRecognised).reason)
    }
}
