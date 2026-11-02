package com.drivecinema.desktop.browse

import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.player.WatchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Task L.1: sorting, filtering and badges of the desktop's Films and Series views. */
class LibraryBrowseTest {

    private fun card(title: String, year: Int? = null, rating: Double? = null, genres: List<String> = emptyList(), state: WatchState = WatchState.NOT_STARTED) =
        BrowseCard(id = title, title = title, year = year, posterPath = null, rating = rating, genres = genres, watchState = state)

    private val alien = card("Alien", 1979, 8.2, listOf("Horror", "Science Fiction"), WatchState.WATCHED)
    private val dune = card("Dune", 2021, 7.8, listOf("Science Fiction"), WatchState.IN_PROGRESS)
    private val brave = card("brave", 2012, null, listOf("Animation"))
    private val unknown = card("Zeta", null, 0.0)
    private val all = listOf(dune, unknown, alien, brave)

    private fun titles(cards: List<BrowseCard>) = cards.map { it.title }

    @Test
    fun sortsByTitleIgnoringCase() {
        assertEquals(listOf("Alien", "brave", "Dune", "Zeta"), titles(LibraryBrowse.apply(all, BrowseOptions())))
    }

    @Test
    fun sortsByYearAndRatingNewestAndBestFirstWithUnknownLast() {
        assertEquals(listOf("Dune", "brave", "Alien", "Zeta"), titles(LibraryBrowse.apply(all, BrowseOptions(sort = BrowseSort.YEAR))))
        // A rating of 0 means TMDB has none: it goes last like a missing one.
        assertEquals(listOf("Alien", "Dune", "brave", "Zeta"), titles(LibraryBrowse.apply(all, BrowseOptions(sort = BrowseSort.RATING))))
    }

    @Test
    fun filtersByGenreAndWatchState() {
        assertEquals(listOf("Alien", "Dune"), titles(LibraryBrowse.apply(all, BrowseOptions(genre = "Science Fiction"))))
        assertEquals(listOf("brave", "Zeta"), titles(LibraryBrowse.apply(all, BrowseOptions(watch = WatchFilter.UNWATCHED))))
        assertEquals(listOf("Dune"), titles(LibraryBrowse.apply(all, BrowseOptions(watch = WatchFilter.IN_PROGRESS))))
        assertEquals(listOf("Alien"), titles(LibraryBrowse.apply(all, BrowseOptions(genre = "Horror", watch = WatchFilter.WATCHED))))
        assertTrue(LibraryBrowse.apply(all, BrowseOptions(genre = "Animation", watch = WatchFilter.WATCHED)).isEmpty())
        assertTrue(BrowseOptions(genre = "Horror").isFiltered)
        assertFalse(BrowseOptions(sort = BrowseSort.YEAR).isFiltered)
    }

    @Test
    fun genresAreSplitTrimmedAndListedOnce() {
        assertEquals(listOf("Comedy", "Animation"), LibraryBrowse.genresOf("Comedy, Animation"))
        assertEquals(emptyList<String>(), LibraryBrowse.genresOf(null))
        assertEquals(listOf("Animation", "Horror", "Science Fiction"), LibraryBrowse.genres(all))
    }

    private fun history(id: String, type: MediaType, position: Long, completed: Boolean, seriesId: String? = null) =
        WatchHistoryRow(id, id, type, null, null, seriesId, null, null, position, 1_000, completed, 1)

    @Test
    fun filmCardsTakeTheirStateAndProgressFromHistory() {
        val movies = listOf(
            MediaItemEntity("a", MediaType.MOVIE, "A", parentFolderId = "f", genres = "Drama"),
            MediaItemEntity("b", MediaType.MOVIE, "B", parentFolderId = "f"),
            MediaItemEntity("c", MediaType.MOVIE, "C", parentFolderId = "f")
        )
        val cards = LibraryBrowse.filmCards(movies, listOf(history("a", MediaType.MOVIE, 250, false), history("b", MediaType.MOVIE, 1_000, true)))
            .associateBy { it.id }
        assertEquals(WatchState.IN_PROGRESS, cards.getValue("a").watchState)
        assertEquals(0.25f, cards.getValue("a").progress, 0.001f)
        assertEquals(listOf("Drama"), cards.getValue("a").genres)
        assertEquals(WatchState.WATCHED, cards.getValue("b").watchState)
        assertEquals(0f, cards.getValue("b").progress, 0f)
        assertEquals(WatchState.NOT_STARTED, cards.getValue("c").watchState)
    }

    @Test
    fun seriesIsWatchedOnlyWhenEveryEpisodeIs() {
        val shows = listOf(SeriesEntity("s1", "Done"), SeriesEntity("s2", "Half"), SeriesEntity("s3", "New"), SeriesEntity("s4", "Started"))
        val counts = mapOf("s1" to 2, "s2" to 10, "s3" to 4, "s4" to 3)
        val rows = listOf(
            history("e1", MediaType.EPISODE, 1_000, true, "s1"),
            history("e2", MediaType.EPISODE, 1_000, true, "s1"),
            history("e3", MediaType.EPISODE, 1_000, true, "s2"),
            history("e4", MediaType.EPISODE, 1_000, true, "s2"),
            history("e5", MediaType.EPISODE, 1_000, true, "s2"),
            history("e6", MediaType.EPISODE, 300, false, "s4")
        )
        val cards = LibraryBrowse.seriesCards(shows, counts, rows).associateBy { it.id }

        assertEquals(WatchState.WATCHED, cards.getValue("s1").watchState)
        assertEquals(WatchState.IN_PROGRESS, cards.getValue("s2").watchState)
        assertEquals("3 of 10 watched", LibraryBrowse.seriesProgressLabel(cards.getValue("s2")))
        assertEquals(WatchState.NOT_STARTED, cards.getValue("s3").watchState)
        assertNull(LibraryBrowse.seriesProgressLabel(cards.getValue("s3")))
        // Started but nothing finished: in progress, no "0 of 3" label.
        assertEquals(WatchState.IN_PROGRESS, cards.getValue("s4").watchState)
        assertNull(LibraryBrowse.seriesProgressLabel(cards.getValue("s4")))
    }
}
