package com.drivecinema.app.core.naming

import com.drivecinema.app.core.metadata.FakeTmdb
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.TmdbClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tasks R.2/R.3: the naming helper's screen state on top of R.1's [NamingGuide]: search, pick, seasons loaded only
 * when opened, the layout chips, films, and no TMDB.
 */
class NamingGuideModelTest {

    private val tmdb = FakeTmdb.library()

    @After
    fun tearDown() = tmdb.close()

    private fun CoroutineScope.model(tmdbAvailable: Boolean = true, liveSearchDelayMs: Long = LIVE_DELAY) =
        NamingGuideModel(NamingGuide(tmdb.client), this, tmdbAvailable, liveSearchDelayMs)

    private fun searches(path: String = "/search/tv") = tmdb.paths.count { it == path }

    private suspend fun NamingGuideModel.awaitState(test: (NamingGuideState) -> Boolean): NamingGuideState =
        withTimeout(10_000) { state.first(test) }

    private suspend fun NamingGuideModel.awaitSeries(): NamingPick.Series =
        awaitState { (it.picked as? NamingPick.Series)?.isLoading == false }.picked as NamingPick.Series

    private suspend fun NamingGuideModel.awaitSeason(season: Int): NamingSeasonSection =
        awaitState { s -> (s.picked as? NamingPick.Series)?.sections?.get(season)?.let { !it.isLoading && it.table != null } == true }
            .let { (it.picked as NamingPick.Series).sections.getValue(season) }

    private suspend fun NamingGuideModel.searchFor(query: String): NamingGuideState {
        setQuery(query)
        search()
        return awaitState { !it.isSearching && (it.seriesResults != null || it.filmResults != null || it.searchFailed) }
    }

    @Test
    fun pickingTheAnimeOnePieceGivesTheTaggedFolderAndClosedSeasons() = runBlocking {
        val model = model()
        val results = model.searchFor("One Piece").seriesResults!!
        assertEquals(setOf(111110, 37854), results.map { it.metadata.tmdbId }.toSet())

        model.pickSeries(results.single { it.animated })
        val picked = model.awaitSeries()

        assertEquals("One Piece (Anime) (1999)", picked.plan?.folderName)
        assertEquals("Series/One Piece (Anime) (1999)/", NamingTexts.seriesFolderPath(picked.plan!!.folderName))
        assertEquals(true, picked.plan.folderFindsShow)
        assertEquals("specials last", listOf(1, 2, 3), picked.orderedSections.take(3).map { it.season })
        assertEquals(0, picked.orderedSections.last().season)
        assertTrue("every season starts closed, nothing loaded", picked.sections.values.none { it.isOpen || it.table != null })
    }

    @Test
    fun openingASeasonLoadsItOnceAndTheRunningNumbersChipReusesIt() = runBlocking {
        val model = model()
        model.pickSeries(model.searchFor("One Piece").seriesResults!!.single { it.animated })
        model.awaitSeries()
        val requestsBefore = tmdb.paths.size

        model.toggleSeason(22)
        val season = model.awaitSeason(22)
        assertTrue(season.isOpen)
        assertEquals("One Piece S22E1086.mkv", season.table!!.rows.first().fileName)
        assertEquals("Season 22", season.table.rows.first().seasonFolder)
        assertTrue(season.table.rows.all { it.readsBack })
        assertTrue("One Piece numbers its episodes through the seasons", season.runningNumbersOffered)
        assertEquals(
            "the season has its own name (\"Arc 22\"), so the arc chip shows too",
            listOf(NamingGuide.Layout.SHOW_SEASON, NamingGuide.Layout.ARC_FOLDER, NamingGuide.Layout.RUNNING_NUMBER),
            NamingTexts.layouts(season)
        )
        assertEquals("one request for the opened season", requestsBefore + 1, tmdb.paths.size)

        model.setLayout(22, NamingGuide.Layout.RUNNING_NUMBER)
        val running = model.awaitState { s ->
            (s.picked as NamingPick.Series).sections.getValue(22).table?.layout == NamingGuide.Layout.RUNNING_NUMBER
        }.let { (it.picked as NamingPick.Series).sections.getValue(22) }
        assertEquals("One Piece - 1086.mkv", running.table!!.rows.first().fileName)
        assertEquals("Season 01", running.table.rows.first().seasonFolder)
        // Reading "Season 01/One Piece - 1086.mkv" back goes through the sync's matcher, which looks at season 1 first:
        // that one season, nothing else.
        assertEquals(listOf("/tv/37854/season/1"), tmdb.paths.drop(requestsBefore + 1))

        model.toggleSeason(22)
        val closed = (model.state.value.picked as NamingPick.Series).sections.getValue(22)
        assertFalse(closed.isOpen)
        assertEquals("closing keeps the loaded rows", running.table, closed.table)
    }

    @Test
    fun theArcChipGivesTheArcItsOwnFolder() = runBlocking {
        val model = model()
        model.pickSeries(model.searchFor("Bleach").seriesResults!!.first { it.metadata.tmdbId == 30984 })
        val picked = model.awaitSeries()
        assertTrue(picked.sections.getValue(2).arcOffered)
        assertFalse(picked.sections.getValue(1).arcOffered)

        model.toggleSeason(2)
        assertEquals("Bleach (2004)", model.awaitSeason(2).table!!.seriesFolder)
        model.setLayout(2, NamingGuide.Layout.ARC_FOLDER)
        val arc = model.awaitState { s ->
            (s.picked as NamingPick.Series).sections.getValue(2).table?.layout == NamingGuide.Layout.ARC_FOLDER
        }.let { (it.picked as NamingPick.Series).sections.getValue(2).table!! }
        assertEquals("Bleach Thousand-Year Blood War (2022)", arc.seriesFolder)
        assertEquals("Bleach Thousand-Year Blood War S02E01.mkv", arc.rows[13].fileName)
    }

    @Test
    fun aFilmGetsItsFilmsNameChecked() = runBlocking {
        val model = model()
        model.setKind(NamingSearchKind.FILMS)
        val films = model.searchFor("Ted").filmResults!!
        model.pickFilm(films.single { it.tmdbId == 72105 })
        val picked = model.awaitState { (it.picked as? NamingPick.Film)?.isLoading == false }.picked as NamingPick.Film

        assertEquals("Films/Ted (2012).mkv", NamingTexts.filmPath(picked.plan!!))
        assertEquals(true, picked.plan.appFindsIt)
        assertNull(NamingTexts.filmCheck(picked.plan))

        model.closePick()
        assertNull(model.state.value.picked)
        assertEquals("the results stay for Back", films, model.state.value.filmResults)
    }

    @Test
    fun switchingTheKindDropsTheOtherKindsResults() = runBlocking {
        val model = model()
        model.searchFor("Ted")
        model.setKind(NamingSearchKind.FILMS)
        assertNull(model.state.value.seriesResults)
        assertEquals("Ted", model.state.value.query)
    }

    @Test
    fun withoutTmdbNothingIsAsked() = runBlocking {
        val model = model(tmdbAvailable = false)
        model.setQuery("Breaking Bad")
        model.search()

        assertFalse(model.state.value.isSearching)
        assertNull(model.state.value.seriesResults)
        assertTrue(tmdb.paths.isEmpty())
    }

    @Test
    fun anEmptyQuerySearchesNothing() = runBlocking {
        val model = model()
        model.setQuery("   ")
        model.search()
        assertFalse(model.state.value.isSearching)
        assertTrue(tmdb.paths.isEmpty())
    }

    // ---- The TV's names panel ----

    @Test
    fun thePreviewGivesThePickedNamesWithoutAskingTmdb() = runBlocking {
        val model = model()
        val results = model.searchFor("One Piece").seriesResults!!
        val anime = results.single { it.animated }
        val requests = tmdb.paths.size

        val preview = NamingGuide.seriesPreview(anime, results)
        assertEquals(NamingGuide.SeriesPreview("One Piece (Anime) (1999)", "Season 01", "One Piece S01E01.mkv"), preview)
        assertEquals(
            "Series/One Piece (Anime) (1999)/\n  Season 01/\n    One Piece S01E01.mkv",
            NamingTexts.previewTree(preview)
        )
        assertEquals("no request for a preview", requests, tmdb.paths.size)

        model.pickSeries(anime)
        assertEquals("the pick names the same folder", preview.folderName, model.awaitSeries().plan!!.folderName)
    }

    @Test
    fun aFilmsPreviewIsItsFilmsName() = runBlocking {
        val model = model()
        model.setKind(NamingSearchKind.FILMS)
        val ted = model.searchFor("Ted").filmResults!!.single { it.tmdbId == 72105 }
        model.pickFilm(ted)
        val plan = (model.awaitState { (it.picked as? NamingPick.Film)?.isLoading == false }.picked as NamingPick.Film).plan!!
        assertEquals(plan.fileName, NamingGuide.filmPreview(ted))
        assertEquals("Films/Ted (2012).mkv", NamingTexts.filmPath(NamingGuide.filmPreview(ted)))
    }

    // ---- Search as you type ----

    @Test
    fun typingSearchesOnceTheTypingStops() = runBlocking {
        val model = model()
        listOf("O", "On", "One", "One ", "One P", "One Piece").forEach(model::setQuery)
        val state = model.awaitState { !it.isSearching && it.seriesResults != null }

        assertEquals("each letter cancels the search before it", 1, searches())
        assertTrue(state.seriesResults!!.any { it.metadata.tmdbId == 37854 })
    }

    @Test
    fun oneLetterAsksNothing() = runBlocking {
        val model = model()
        model.setQuery("O")
        delay(LIVE_DELAY * 4)
        assertFalse(model.state.value.isSearching)
        assertTrue(tmdb.paths.isEmpty())
    }

    @Test
    fun resultsStayWhileATypoIsFixedAndGoWithAnEmptyField() = runBlocking {
        val model = model()
        val results = model.searchFor("Bleach").seriesResults!!
        model.setQuery("B")
        delay(LIVE_DELAY * 4)
        assertEquals(results, model.state.value.seriesResults)

        model.setQuery("")
        assertNull(model.state.value.seriesResults)
        assertEquals(1, searches())
    }

    @Test
    fun theSearchKeySearchesAtOnceAndOnlyOnce() = runBlocking {
        // A pause far longer than the wait below: only the Search key can have asked.
        val model = model(liveSearchDelayMs = 60_000)
        model.setQuery("Bleach")
        model.search()
        model.awaitState { !it.isSearching && it.seriesResults != null }

        model.search()
        assertFalse("the same query again asks TMDB nothing", model.state.value.isSearching)
        assertEquals(1, searches())
    }

    @Test
    fun aNewerQueryWins() = runBlocking {
        val model = model()
        model.setQuery("One Piece")
        model.search()
        model.setQuery("Bleach")
        model.search()
        val state = model.awaitState { !it.isSearching && it.seriesResults != null }
        assertTrue(state.seriesResults!!.isNotEmpty())
        assertTrue(state.seriesResults.none { it.metadata.tmdbId == 37854 })
    }

    @Test
    fun aPickIsNotClosedByASearchStillWaitingForTheTyping() = runBlocking {
        val model = model()
        model.setKind(NamingSearchKind.FILMS)
        val films = model.searchFor("Ted").filmResults!!
        val ted = films.single { it.tmdbId == 72105 }

        // The remote reaches OK before the typing pause is over.
        model.setQuery("Ted 2")
        model.pickFilm(ted)
        delay(LIVE_DELAY * 4)

        assertEquals(ted, (model.state.value.picked as? NamingPick.Film)?.film)
        // (The pick's own name check searches TMDB too, so the requests aren't counted.)
        assertEquals("\"Ted 2\" was never searched", films, model.state.value.filmResults)
    }

    @Test
    fun switchingTheKindSearchesTheQueryAgain() = runBlocking {
        val model = model()
        model.searchFor("Ted")
        model.setKind(NamingSearchKind.FILMS)
        val films = model.awaitState { !it.isSearching && it.filmResults != null }.filmResults!!
        assertTrue(films.any { it.tmdbId == 72105 })
        assertNull(model.state.value.seriesResults)
    }

    @Test
    fun tmdbDownIsSaid() = runBlocking {
        // Nothing listens on port 1.
        val down = TmdbClient(apiKey = "test_key", baseUrl = "http://127.0.0.1:1/3")
        val model = NamingGuideModel(NamingGuide(down), this, tmdbAvailable = true)
        val state = model.searchFor("Breaking Bad")
        assertTrue(state.searchFailed)
        assertNull(state.seriesResults)
    }

    private companion object {
        /** A short typing pause, so the tests don't wait 350 ms each. */
        const val LIVE_DELAY = 50L
    }
}
