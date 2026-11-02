package com.drivecinema.app.core.metadata

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task R.1, the milestone's hard requirement: every name the naming guide suggests, read back the way the sync reads
 * a Drive folder (the folder name finds the show, each season folder and file name gives a season and episode, and the
 * matcher finds TMDB's episode), is the episode it was suggested for. Each series' files are matched together, as a
 * sync does; the matcher runs against TMDB directly, not the guide's cache.
 */
class NamingRoundTripTest {

    private val tmdb = FakeTmdb.library()

    @After
    fun tearDown() = tmdb.close()

    private fun guide() = NamingGuide(tmdb.client)

    private suspend fun plan(guide: NamingGuide, query: String, tvId: Int, year: Int? = null): NamingGuide.SeriesPlan {
        val results = guide.searchSeries(query, year).getOrThrow()
        val chosen = results.first { it.metadata.tmdbId == tvId }
        return guide.seriesPlan(chosen, results).getOrThrow()
    }

    /** Every row reads back in the guide, and the sync reads every row's names as that row's episode. */
    private suspend fun assertRoundTrip(tvId: Int, tables: List<NamingGuide.SeasonTable>) {
        val rows = tables.flatMap { it.rows }
        assertTrue(rows.isNotEmpty())
        rows.forEach { assertTrue("guide reads back ${it.path}", it.readsBack) }
        for ((folder, inFolder) in rows.groupBy { it.seriesFolder }) {
            val read = tmdb.syncReads(folder, inFolder.map { it.seasonFolder to it.fileName })
            assertNotNull("the sync finds a show for '$folder'", read)
            assertEquals("'$folder' finds the chosen show", tvId, read!!.tvId)
            for (row in inFolder) {
                val back = read.episodes[row.seasonFolder to row.fileName]
                assertEquals(row.path, row.episode.seasonNumber to row.episode.episodeNumber, back?.let { it.seasonNumber to it.episodeNumber })
            }
        }
    }

    @Test
    fun breakingBadPlainSeasonsAndSpecials() = runBlocking {
        val guide = guide()
        val plan = plan(guide, "Breaking Bad", 1396)
        assertEquals("Breaking Bad (2008)", plan.folderName)
        assertEquals(true, plan.folderFindsShow)
        val tables = listOf(0, 1, 2).map { guide.season(plan, it, NamingGuide.Layout.SHOW_SEASON).getOrThrow() }
        assertEquals("Series/Breaking Bad (2008)/Season 01/Breaking Bad S01E01.mkv", tables[1].rows.first().path)
        assertEquals("Season 00", tables[0].rows.first().seasonFolder)
        assertRoundTrip(1396, tables)
    }

    @Test
    fun tedWithItsYearBesideTheMorePopularTedLasso() = runBlocking {
        val guide = guide()
        val results = guide.searchSeries("Ted").getOrThrow()
        assertEquals("Ted Lasso comes first, as on TMDB", 97546, results.first().metadata.tmdbId)
        val plan = guide.seriesPlan(results.first { it.metadata.tmdbId == 201834 }, results).getOrThrow()
        assertEquals("ted (2024)", plan.folderName)
        assertEquals(true, plan.folderFindsShow)
        assertRoundTrip(201834, listOf(guide.season(plan, 1, NamingGuide.Layout.SHOW_SEASON).getOrThrow()))
    }

    @Test
    fun onePieceAnimeTagRunningNumbersAndFansubOption() = runBlocking {
        val guide = guide()
        val plan = plan(guide, "One Piece", 37854)
        assertEquals("One Piece (Anime) (1999)", plan.folderName)
        assertEquals(true, plan.folderFindsShow)

        val default = listOf(0, 1, 2, 22, 23).map { guide.season(plan, it, NamingGuide.Layout.SHOW_SEASON).getOrThrow() }
        val season22 = default[3]
        assertEquals("One Piece S22E1086.mkv", season22.rows.first().fileName)
        assertEquals("Season 22", season22.rows.first().seasonFolder)
        assertRoundTrip(37854, default)

        val episodes22 = guide.episodes(plan, 22).getOrThrow()
        assertTrue(guide.runningNumbersOffered(plan, 22, episodes22))
        assertFalse("season 1 is the same either way", guide.runningNumbersOffered(plan, 1, guide.episodes(plan, 1).getOrThrow()))
        val fansub = listOf(2, 22, 23).map { guide.season(plan, it, NamingGuide.Layout.RUNNING_NUMBER).getOrThrow() }
        fansub.forEach { assertEquals(NamingGuide.Layout.RUNNING_NUMBER, it.layout) }
        assertEquals("Season 01", fansub[1].rows.first().seasonFolder)
        assertEquals("One Piece - 1086.mkv", fansub[1].rows.first().fileName)
        assertEquals("One Piece - 1180.mkv", fansub[2].rows.last().fileName)
        assertRoundTrip(37854, fansub)
    }

    @Test
    fun bleachShowSeasonByDefaultAndTheArcAsItsOwnFolder() = runBlocking {
        val guide = guide()
        val plan = plan(guide, "Bleach", 30984)
        assertEquals("Bleach (2004)", plan.folderName)

        val default = listOf(0, 1, 2).map { guide.season(plan, it, NamingGuide.Layout.SHOW_SEASON).getOrThrow() }
        assertEquals("Bleach S02E14.mkv", default[2].rows[13].fileName)
        assertRoundTrip(30984, default)

        assertFalse("season 1 is named like the show", guide.arcOffered(plan, 1))
        assertFalse(guide.arcOffered(plan, 0))
        assertTrue(guide.arcOffered(plan, 2))
        val arc = guide.season(plan, 2, NamingGuide.Layout.ARC_FOLDER).getOrThrow()
        assertEquals(NamingGuide.Layout.ARC_FOLDER, arc.layout)
        assertEquals("Bleach Thousand-Year Blood War (2022)", arc.seriesFolder)
        assertEquals(true, arc.folderFindsShow)
        val episode14 = arc.rows.first { it.episode.episodeNumber == 14 }
        assertEquals("Season 02", episode14.seasonFolder)
        assertEquals("Bleach Thousand-Year Blood War S02E01.mkv", episode14.fileName)
        assertEquals("Season 04", arc.rows.last().seasonFolder)
        assertRoundTrip(30984, listOf(arc))
    }

    /** F-48: the phone said "with this folder name the app finds another show" once TMDB had an empty TYBW entry. */
    @Test
    fun bleachArcFolderStillFindsBleachBesideABareTmdbEntry() = runBlocking {
        tmdb.shows += FakeTmdb.bareThousandYearBloodWar
        val guide = guide()
        val plan = plan(guide, "Bleach", 30984)
        val arc = guide.season(plan, 2, NamingGuide.Layout.ARC_FOLDER).getOrThrow()
        assertEquals("Bleach Thousand-Year Blood War (2022)", arc.seriesFolder)
        assertEquals(true, arc.folderFindsShow)
        assertRoundTrip(30984, listOf(arc))
    }

    @Test
    fun layoutThatDoesNotApplyFallsBackToTheShowsSeason() = runBlocking {
        val guide = guide()
        val plan = plan(guide, "Breaking Bad", 1396)
        assertEquals(NamingGuide.Layout.SHOW_SEASON, guide.season(plan, 2, NamingGuide.Layout.ARC_FOLDER).getOrThrow().layout)
        assertEquals(NamingGuide.Layout.SHOW_SEASON, guide.season(plan, 2, NamingGuide.Layout.RUNNING_NUMBER).getOrThrow().layout)
    }

    @Test
    fun filmsReadBackThroughTheAppsOwnFilmSearch() = runBlocking {
        val guide = guide()
        val ted = guide.searchFilms("Ted").getOrThrow().first { it.tmdbId == 72105 }
        val tedPlan = guide.filmPlan(ted)
        assertEquals("Ted (2012).mkv", tedPlan.fileName)
        assertEquals(true, tedPlan.appFindsIt)

        val blade = guide.searchFilms("Blade Runner").getOrThrow().first { it.tmdbId == 335984 }
        assertEquals("F-44: the year in the title stays in it", true, guide.filmPlan(blade).appFindsIt)

        val second = guide.searchFilms("Alpha").getOrThrow().first { it.tmdbId == 2 }
        val alpha = guide.filmPlan(second)
        assertEquals("two films with one name and year: the app takes TMDB's first", false, alpha.appFindsIt)
        assertEquals(1, alpha.appPicks?.tmdbId)
    }

    @Test
    fun openingOnePieceAndOneSeasonCostsFourRequests() = runBlocking {
        val guide = guide()
        val plan = plan(guide, "One Piece", 37854)
        guide.season(plan, 22, NamingGuide.Layout.SHOW_SEASON).getOrThrow()
        guide.season(plan, 22, NamingGuide.Layout.SHOW_SEASON).getOrThrow()
        assertEquals(
            "search, season list, the folder's own search, season 22 once",
            listOf("/search/tv", "/tv/37854", "/search/tv", "/tv/37854/season/22"),
            tmdb.paths.toList()
        )
    }
}
