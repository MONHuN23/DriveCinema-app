package com.drivecinema.app.ui.mobile

import com.drivecinema.app.core.database.dao.SeriesEpisodeCount
import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.mobile.grid.LibrarySort
import com.drivecinema.app.ui.mobile.grid.LibraryWatchFilter
import com.drivecinema.app.ui.mobile.grid.MobileLibrary
import com.drivecinema.app.ui.player.WatchState
import org.junit.Assert.assertEquals
import org.junit.Test

/** The phone Library's sort and watch-state chips (same rules as the desktop's `LibraryBrowse`). */
class MobileLibraryTest {

    private fun movie(id: String, title: String, year: Int? = null, rating: Double? = null) =
        MediaItemEntity(id = id, type = MediaType.MOVIE, title = title, year = year, voteAverage = rating, parentFolderId = "f")

    private fun film(id: String, positionMs: Long, completed: Boolean = false) = WatchHistoryRow(
        id = id, title = id, type = MediaType.MOVIE, posterPath = null, backdropPath = null, seriesId = null,
        seasonNumber = null, episodeNumber = null, positionMs = positionMs, durationMs = 100_000,
        isCompleted = completed, lastWatchedAt = 1
    )

    private fun episode(seriesId: String, number: Int, positionMs: Long, completed: Boolean = false) = WatchHistoryRow(
        id = "$seriesId-e$number", title = "E$number", type = MediaType.EPISODE, posterPath = null, backdropPath = null,
        seriesId = seriesId, seasonNumber = 1, episodeNumber = number, positionMs = positionMs, durationMs = 100_000,
        isCompleted = completed, lastWatchedAt = 1
    )

    private val movies = listOf(
        movie("dune2", "Dune Part Two", 2024, 8.2),
        movie("dune1", "Dune Part One", 2021, 7.8),
        movie("tangled", "aranyhaj", 2010, 7.4),
        movie("noinfo", "Zed", null, null)
    )
    private val history = listOf(film("dune1", 0, completed = true), film("dune2", 40_000))

    @Test
    fun filmStatesFollowTheWatchRules() {
        val states = MobileLibrary.filmStates(history)
        assertEquals(WatchState.WATCHED, states["dune1"])
        assertEquals(WatchState.IN_PROGRESS, states["dune2"])
        val entries = MobileLibrary.films(movies, states)
        assertEquals(WatchState.NOT_STARTED, entries.single { it.item.id == "tangled" }.watchState)
    }

    @Test
    fun seriesIsWatchedOnlyWhenEveryEpisodeIs() {
        val rows = listOf(
            episode("done", 1, 0, completed = true), episode("done", 2, 0, completed = true),
            episode("half", 1, 0, completed = true),
            episode("started", 1, 5_000)
        )
        val counts = listOf(SeriesEpisodeCount("done", 2), SeriesEpisodeCount("half", 10), SeriesEpisodeCount("started", 3))
        val states = MobileLibrary.seriesStates(rows, counts)
        assertEquals(WatchState.WATCHED, states["done"])
        assertEquals(WatchState.IN_PROGRESS, states["half"])
        assertEquals(WatchState.IN_PROGRESS, states["started"])
        val entries = MobileLibrary.series(listOf(SeriesEntity("fresh", "Fresh")), states)
        assertEquals(WatchState.NOT_STARTED, entries.single().watchState)
    }

    @Test
    fun filtersByWatchStateAndCountsEachChip() {
        val entries = MobileLibrary.films(movies, MobileLibrary.filmStates(history))
        assertEquals(listOf("dune2"), MobileLibrary.apply(entries, LibrarySort.TITLE, LibraryWatchFilter.IN_PROGRESS).map { it.item.id })
        assertEquals(listOf("dune1"), MobileLibrary.apply(entries, LibrarySort.TITLE, LibraryWatchFilter.WATCHED).map { it.item.id })
        assertEquals(
            mapOf(
                LibraryWatchFilter.ALL to 4, LibraryWatchFilter.UNWATCHED to 2,
                LibraryWatchFilter.IN_PROGRESS to 1, LibraryWatchFilter.WATCHED to 1
            ),
            MobileLibrary.counts(entries)
        )
    }

    @Test
    fun sortsByTitleIgnoringCaseAndByYearOrRatingWithUnknownLast() {
        val entries = MobileLibrary.films(movies, emptyMap())
        assertEquals(
            listOf("tangled", "dune1", "dune2", "noinfo"),
            MobileLibrary.apply(entries, LibrarySort.TITLE, LibraryWatchFilter.ALL).map { it.item.id }
        )
        assertEquals(
            listOf("dune2", "dune1", "tangled", "noinfo"),
            MobileLibrary.apply(entries, LibrarySort.YEAR, LibraryWatchFilter.ALL).map { it.item.id }
        )
        assertEquals(
            listOf("dune2", "dune1", "tangled", "noinfo"),
            MobileLibrary.apply(entries, LibrarySort.RATING, LibraryWatchFilter.ALL).map { it.item.id }
        )
    }
}
