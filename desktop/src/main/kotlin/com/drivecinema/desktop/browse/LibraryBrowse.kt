package com.drivecinema.desktop.browse

import com.drivecinema.app.core.database.dao.WatchHistoryRow
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.ui.player.WatchState

/** Task L.1: the desktop's Films and Series views. */
enum class BrowseKind(val label: String) { FILMS("Films"), SERIES("Series") }

enum class BrowseSort(val label: String) { TITLE("Title"), YEAR("Year"), RATING("Rating") }

/** Filter by watch state (the J.1 rules: finished = watched, any position = in progress). */
enum class WatchFilter(val label: String) { ALL("All"), UNWATCHED("Unwatched"), IN_PROGRESS("In progress"), WATCHED("Watched") }

/** What a Films or Series view shows; kept while the app runs. [genre] null = every genre. */
data class BrowseOptions(
    val sort: BrowseSort = BrowseSort.TITLE,
    val genre: String? = null,
    val watch: WatchFilter = WatchFilter.ALL
) {
    val isFiltered: Boolean get() = genre != null || watch != WatchFilter.ALL
}

/** One poster in Home's rows or a browse grid, with what its badges need. */
data class BrowseCard(
    val id: String,
    val title: String,
    val year: Int?,
    val posterPath: String?,
    val rating: Double?,
    val genres: List<String>,
    val watchState: WatchState,
    /** Films in progress: 0–1 for the bar under the poster. */
    val progress: Float = 0f,
    /** Series only. */
    val watchedEpisodes: Int = 0,
    val totalEpisodes: Int = 0
)

/** Films and series as cards, both sorted by title (the database order). */
data class LibraryCards(val films: List<BrowseCard> = emptyList(), val series: List<BrowseCard> = emptyList()) {
    fun of(kind: BrowseKind): List<BrowseCard> = if (kind == BrowseKind.FILMS) films else series
}

/** Pure functions behind the views, unit-tested in `LibraryBrowseTest`. */
object LibraryBrowse {

    /** "Comedy, Animation" → ["Comedy", "Animation"] (the sync engine stores TMDB genres comma-separated). */
    fun genresOf(genres: String?): List<String> =
        genres.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** [history]: the latest progress per catalog title (films and episodes), from the watch-history query. */
    fun filmCards(movies: List<MediaItemEntity>, history: List<WatchHistoryRow>): List<BrowseCard> {
        val progress = history.filter { it.type == MediaType.MOVIE }.associateBy { it.id }
        return movies.map { movie ->
            val row = progress[movie.id]
            BrowseCard(
                id = movie.id,
                title = movie.title,
                year = movie.year,
                posterPath = movie.posterPath,
                rating = movie.voteAverage,
                genres = genresOf(movie.genres),
                watchState = row?.let { WatchState.of(it.positionMs, it.isCompleted) } ?: WatchState.NOT_STARTED,
                progress = row?.takeIf { !it.isCompleted }?.let { BrowseModels.progressFraction(it.positionMs, it.durationMs) } ?: 0f
            )
        }
    }

    /**
     * A series is watched when every playable episode is, in progress when any episode was started or finished,
     * otherwise unwatched.
     */
    fun seriesCards(series: List<SeriesEntity>, episodeCounts: Map<String, Int>, history: List<WatchHistoryRow>): List<BrowseCard> {
        val episodes = history.filter { it.type == MediaType.EPISODE && it.seriesId != null }.groupBy { it.seriesId!! }
        return series.map { show ->
            val rows = episodes[show.id].orEmpty()
            val total = episodeCounts[show.id] ?: 0
            val watched = rows.count { it.isCompleted }.coerceAtMost(total)
            val started = rows.any { it.isCompleted || it.positionMs > 0 }
            BrowseCard(
                id = show.id,
                title = show.title,
                year = show.year,
                posterPath = show.posterPath,
                rating = show.voteAverage,
                genres = genresOf(show.genres),
                watchState = when {
                    total > 0 && watched >= total -> WatchState.WATCHED
                    started -> WatchState.IN_PROGRESS
                    else -> WatchState.NOT_STARTED
                },
                watchedEpisodes = watched,
                totalEpisodes = total
            )
        }
    }

    /** Filters, then sorts. Year and rating: highest first, unknown last; ties by title. */
    fun apply(cards: List<BrowseCard>, options: BrowseOptions): List<BrowseCard> {
        val byTitle = compareBy<BrowseCard, String>(String.CASE_INSENSITIVE_ORDER) { it.title }
        val comparator = when (options.sort) {
            BrowseSort.TITLE -> byTitle
            BrowseSort.YEAR -> compareBy<BrowseCard> { it.year == null }.thenByDescending { it.year ?: 0 }.then(byTitle)
            BrowseSort.RATING -> compareBy<BrowseCard> { (it.rating ?: 0.0) <= 0.0 }.thenByDescending { it.rating ?: 0.0 }.then(byTitle)
        }
        return cards
            .filter { options.genre == null || options.genre in it.genres }
            .filter {
                when (options.watch) {
                    WatchFilter.ALL -> true
                    WatchFilter.UNWATCHED -> it.watchState == WatchState.NOT_STARTED
                    WatchFilter.IN_PROGRESS -> it.watchState == WatchState.IN_PROGRESS
                    WatchFilter.WATCHED -> it.watchState == WatchState.WATCHED
                }
            }
            .sortedWith(comparator)
    }

    /** Every genre in [cards], alphabetical, for the genre menu. */
    fun genres(cards: List<BrowseCard>): List<String> = cards.flatMap { it.genres }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Under a series poster: "3 of 10 watched" once anything was watched; null otherwise. */
    fun seriesProgressLabel(card: BrowseCard): String? =
        if (card.totalEpisodes > 0 && card.watchedEpisodes > 0) "${card.watchedEpisodes} of ${card.totalEpisodes} watched" else null

    /** Home shows this many posters per row, with "See all" for the rest. */
    const val HOME_ROW_LIMIT = 20
}
