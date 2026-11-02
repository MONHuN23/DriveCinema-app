package com.drivecinema.app.core.naming

import com.drivecinema.app.core.metadata.FakeTmdb
import com.drivecinema.app.core.metadata.FolderCheck
import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import com.drivecinema.app.core.metadata.SeriesFolderListing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/**
 * Tasks R.2/R.3: the series page's Naming check on top of R.1's [FolderCheck]: unplaced files first, TMDB's seasons
 * with the files and the missing episodes in episode order, the filter, closed seasons loaded when opened, the
 * user's own episode pick, the copy list, and the cases that never ask Drive.
 */
class NamingCheckModelTest {

    private val tmdb = FakeTmdb.library()
    private var listings = 0

    @After
    fun tearDown() = tmdb.close()

    private fun listing(folder: String, vararg files: Pair<String?, String>) = SeriesFolderListing(
        seriesFolderId = "series",
        seriesFolderName = folder,
        files = files.mapIndexed { i, (season, name) ->
            SeriesFolderListing.ListedFile("f$i", name, folderId = season ?: "series", seasonFolderName = season)
        }
    )

    private val breakingBad = listing(
        "Breaking Bad (2008)",
        "Season 01" to "Breaking Bad S01E01.mkv",
        "Season 01" to "Breaking Bad S01E02.mkv",
        "Season 01" to "Breaking.Bad.S01E02.720p.mkv",
        "Season 01" to "Breaking Bad S02E03.mkv",
        "Season 01" to "Behind the scenes.mkv",
        "Season 02" to "Breaking Bad S02E99.mkv"
    )

    private fun CoroutineScope.model(
        folder: SeriesFolderListing = breakingBad,
        tmdbAvailable: Boolean = true,
        isDriveSeries: Boolean = true,
        read: suspend () -> SeriesFolderListing = { listings++; folder }
    ) = NamingCheckModel(this, tmdb.client, tmdbAvailable, isDriveSeries, read, today = { LocalDate.parse("2026-11-01") })

    private suspend fun NamingCheckModel.awaitReady(): NamingCheckState.Ready =
        withTimeout(10_000) { state.first { it is NamingCheckState.Ready } } as NamingCheckState.Ready

    private fun NamingCheckState.Ready.season(n: Int) = seasons.single { it.season == n }

    private fun NamingCheckRow.label(): String = when (this) {
        is NamingCheckRow.File -> "${result::class.simpleName}:${result.file.name}"
        is NamingCheckRow.Missing -> "Missing:E${missing.episode.episodeNumber}"
        is NamingCheckRow.Suggested -> "Suggested:${row.fileName}"
    }

    @Test
    fun theFilesGoIntoTmdbsSeasonsInEpisodeOrderWithTheMissingOnes() = runBlocking {
        val model = model()
        model.load()
        val ready = model.awaitReady()

        assertEquals(
            "no episode number, and an episode TMDB doesn't have, can't be placed",
            setOf("Behind the scenes.mkv", "Breaking Bad S02E99.mkv"),
            ready.unplaced.map { it.file.name }.toSet()
        )
        val season1 = ready.season(1)
        assertTrue(season1.isOpen && season1.hasFiles)
        assertEquals(
            listOf(
                "Matched:Breaking Bad S01E01.mkv", "Duplicate:Breaking Bad S01E02.mkv", "Duplicate:Breaking.Bad.S01E02.720p.mkv",
                "SeasonMismatch:Breaking Bad S02E03.mkv", "Missing:E4", "Missing:E5", "Missing:E6", "Missing:E7"
            ),
            season1.rows.map { it.label() }
        )
        val season2 = ready.season(2)
        assertFalse("no file is read into season 2", season2.hasFiles || season2.isOpen)
        assertEquals("13 episodes, none on Drive", NamingTexts.noneOnDrive(season2.episodeCount))
        assertEquals("specials last", 0, ready.seasons.last().season)
        assertEquals("Specials (Season 00)", ready.seasons.last().title)
        assertEquals(5, ready.problemCount)
        assertEquals(4, ready.missingCount)
    }

    @Test
    fun theFilterShowsProblemsOrMissingOnly() = runBlocking {
        val model = model()
        model.load()
        model.awaitReady()

        model.setFilter(NamingCheckFilter.PROBLEMS)
        val problems = model.state.value as NamingCheckState.Ready
        assertEquals(3, problems.season(1).rows(problems.filter).size)
        assertTrue(problems.season(1).rows(problems.filter).all { it.isProblem })

        model.setFilter(NamingCheckFilter.MISSING)
        val missing = model.state.value as NamingCheckState.Ready
        assertEquals(listOf("Missing:E4", "Missing:E5", "Missing:E6", "Missing:E7"), missing.season(1).rows(missing.filter).map { it.label() })
        assertTrue("a season without files lists no missing rows", missing.season(2).rows(missing.filter).isEmpty())
    }

    @Test
    fun openingASeasonWithoutFilesShowsTheNamesToUse() = runBlocking {
        val model = model()
        model.load()
        model.awaitReady()

        model.toggleSeason(2)
        val ready = withTimeout(10_000) {
            model.state.first { (it as? NamingCheckState.Ready)?.season(2)?.rows?.isNotEmpty() == true }
        } as NamingCheckState.Ready
        val rows = ready.season(2).rows.map { (it as NamingCheckRow.Suggested).row }
        assertTrue(ready.season(2).isOpen)
        assertEquals(13, rows.size)
        assertEquals("Season 02", rows.first().seasonFolder)
        assertEquals("Breaking Bad S02E01.mkv", rows.first().fileName)
        assertTrue(rows.all { it.readsBack })
    }

    @Test
    fun aFileWithoutASuggestionCanBeGivenAMissingEpisode() = runBlocking {
        val model = model()
        model.load()
        val ready = model.awaitReady()
        val extra = ready.unplaced.single { it.file.name == "Behind the scenes.mkv" }
        assertTrue(ready.canPick(extra))
        assertNull(ready.suggestionFor(extra))
        val matched = ready.season(1).rows.first() as NamingCheckRow.File
        assertFalse("a recognised file needs no pick", ready.canPick(matched.result))

        val e4 = ready.report.missing.first { it.episode.episodeNumber == 4 }
        model.pick(extra.file.fileId, e4)
        val picked = model.state.value as NamingCheckState.Ready
        assertEquals("Season 01/Breaking Bad S01E04.mkv", NamingTexts.suggestionPath(picked.suggestionFor(extra)!!))
        assertTrue(NamingTexts.renameList(picked).lines().contains("Season 01/Behind the scenes.mkv → Season 01/Breaking Bad S01E04.mkv"))
        assertTrue(
            "the check's own suggestions are in the list too",
            NamingTexts.renameList(picked).lines().contains("Season 01/Breaking Bad S02E03.mkv → Season 02/Breaking Bad S02E03.mkv")
        )

        model.pick(extra.file.fileId, null)
        assertNull((model.state.value as NamingCheckState.Ready).suggestionFor(extra))
    }

    @Test
    fun aProblemsReasonAndSuggestionAreSaid() = runBlocking {
        val model = model()
        model.load()
        val ready = model.awaitReady()
        val mismatch = ready.report.files.single { it is FileResult.SeasonMismatch }
        assertEquals("The folder says season 1, the file says season 2. The folder wins.", NamingTexts.reason(mismatch))
        assertEquals(
            "Move and rename to: Season 02/Breaking Bad S02E03.mkv",
            NamingTexts.suggestionLine(ready.suggestionFor(mismatch)!!, mismatch.file)
        )
        val extra = ready.unplaced.single { it.file.name == "Behind the scenes.mkv" }
        assertEquals("No episode number in the name, so the app reads it as episode 1.", NamingTexts.reason(extra))
    }

    @Test
    fun aFolderTmdbDoesntKnowLeavesEveryFileUnplaced() = runBlocking {
        val model = model(listing("Nothing Like It (1950)", "Season 01" to "Nothing S01E01.mkv"))
        model.load()
        val ready = model.awaitReady()
        assertNull(ready.report.show)
        assertEquals(1, ready.unplaced.size)
        assertTrue(ready.seasons.isEmpty())
        assertEquals(FolderCheck.Reason.NOT_ON_TMDB, (ready.unplaced.single() as FileResult.NotRecognised).reason)
    }

    @Test
    fun anArcFolderShowsOnlyItsArc() = runBlocking {
        val model = model(listing(
            "Bleach Thousand-Year Blood War (2022)",
            "Season 01" to "Bleach Thousand-Year Blood War S01E01.mkv",
            "Season 02" to "Bleach Thousand-Year Blood War S02E01.mkv"
        ))
        model.load()
        val ready = model.awaitReady()
        assertEquals(listOf(2), ready.seasons.map { it.season })
        assertEquals("Season 2 · Thousand-Year Blood War", ready.seasons.single().title)
        assertEquals(2, ready.seasons.single().rows.count { it is NamingCheckRow.File })
    }

    @Test
    fun localSeriesAndNoTmdbNeverReadDrive() = runBlocking {
        val local = model(isDriveSeries = false)
        local.load()
        assertEquals(NamingCheckState.LocalSeries, local.state.value)

        val noTmdb = model(tmdbAvailable = false)
        noTmdb.load()
        assertEquals(NamingCheckState.NoTmdb, noTmdb.state.value)

        assertEquals(0, listings)
        assertTrue(tmdb.paths.isEmpty())
    }

    @Test
    fun driveFailingIsSaidAndRetryReadsAgain() = runBlocking {
        var fail = true
        val model = model(read = { listings++; if (fail) throw IOException("offline") else breakingBad })
        model.load()
        assertEquals(NamingCheckState.Failed(drive = true), withTimeout(10_000) { model.state.first { it is NamingCheckState.Failed } })

        fail = false
        model.load()
        model.awaitReady()
        assertEquals(2, listings)
    }
}
