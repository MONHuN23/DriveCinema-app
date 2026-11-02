package com.drivecinema.app.ui.mobile.grid

import com.drivecinema.app.core.database.dao.SeriesEpisodeCount
import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.player.WatchState

/** The phone Library's sort menu; the same orders as the desktop's `BrowseSort`. */
enum class LibrarySort(val label: String) { TITLE("A–Z"), YEAR("Year"), RATING("Rating") }

/** The phone Library's watch-state chips (the J.1 rules: finished = watched, any position = in progress). */
enum class LibraryWatchFilter(val label: String) {
    ALL("All"), UNWATCHED("Unwatched"), IN_PROGRESS("In progress"), WATCHED("Watched");

    fun matches(state: WatchState): Boolean = when (this) {
        ALL -> true
        UNWATCHED -> state == WatchState.NOT_STARTED
        IN_PROGRESS -> state == WatchState.IN_PROGRESS
        WATCHED -> state == WatchState.WATCHED
    }
}

/** One poster in the phone Library grid. */
data class LibraryEntry<T>(
    val item: T,
    val title: String,
    val year: Int?,
    val rating: Double?,
    val watchState: WatchState
)

/**
 * Pure functions behind the phone Library's sort and filter chips, unit-tested in `MobileLibraryTest`. The watch
 * states follow the desktop's `LibraryBrowse` (which lives in `:desktop`), so a title is in the same chip everywhere.
 */
object MobileLibrary {

    /** A film's state from its latest progress in [history] (the watch-history query); not in it = unwatched. */
    fun filmStates(history: List<WatchHistoryRow>): Map<String, WatchState> =
        history.filter { it.type == MediaType.MOVIE }.associate { it.id to WatchState.of(it.positionMs, it.isCompleted) }

    /**
     * A series is watched when every playable episode is, in progress when any episode was started or finished;
     * series without history are left out (unwatched).
     */
    fun seriesStates(history: List<WatchHistoryRow>, episodeCounts: List<SeriesEpisodeCount>): Map<String, WatchState> {
        val totals = episodeCounts.associate { it.seriesId to it.episodeCount }
        return history.filter { it.type == MediaType.EPISODE && it.seriesId != null }
            .groupBy { it.seriesId!! }
            .mapValues { (seriesId, rows) ->
                val total = totals[seriesId] ?: 0
                val watched = rows.count { it.isCompleted }
                when {
                    total > 0 && watched >= total -> WatchState.WATCHED
                    rows.any { it.isCompleted || it.positionMs > 0 } -> WatchState.IN_PROGRESS
                    else -> WatchState.NOT_STARTED
                }
            }
    }

    fun films(movies: List<MediaItemEntity>, states: Map<String, WatchState>): List<LibraryEntry<MediaItemEntity>> =
        movies.map { LibraryEntry(it, it.title, it.year, it.voteAverage, states[it.id] ?: WatchState.NOT_STARTED) }

    fun series(series: List<SeriesEntity>, states: Map<String, WatchState>): List<LibraryEntry<SeriesEntity>> =
        series.map { LibraryEntry(it, it.title, it.year, it.voteAverage, states[it.id] ?: WatchState.NOT_STARTED) }

    /** Filters, then sorts. Year and rating: highest first, unknown last; ties by title. */
    fun <T> apply(entries: List<LibraryEntry<T>>, sort: LibrarySort, filter: LibraryWatchFilter): List<LibraryEntry<T>> {
        val byTitle = compareBy<LibraryEntry<T>, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        val comparator = when (sort) {
            LibrarySort.TITLE -> byTitle
            LibrarySort.YEAR -> compareBy<LibraryEntry<T>> { it.year == null }.thenByDescending { it.year ?: 0 }.then(byTitle)
            LibrarySort.RATING ->
                compareBy<LibraryEntry<T>> { (it.rating ?: 0.0) <= 0.0 }.thenByDescending { it.rating ?: 0.0 }.then(byTitle)
        }
        return entries.filter { filter.matches(it.watchState) }.sortedWith(comparator)
    }

    /** How many entries each chip would show. */
    fun counts(entries: List<LibraryEntry<*>>): Map<LibraryWatchFilter, Int> =
        LibraryWatchFilter.entries.associateWith { filter -> entries.count { filter.matches(it.watchState) } }
}
