package com.drivecinema.app.core.naming

import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.TmdbMetadata
import com.drivecinema.app.core.metadata.TmdbSeriesResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class NamingSearchKind { SERIES, FILMS }

/** One TMDB season in the naming table: closed until opened, then its rows in [layout] (Task R.2/R.3). */
data class NamingSeasonSection(
    val season: Int,
    val isOpen: Boolean = false,
    val isLoading: Boolean = false,
    val failed: Boolean = false,
    /** The layout asked for; [table]'s own layout says which one was used. */
    val layout: NamingGuide.Layout = NamingGuide.Layout.SHOW_SEASON,
    val table: NamingGuide.SeasonTable? = null,
    /** "Arc as its own folder" applies to this season (R.0 §4.4). */
    val arcOffered: Boolean = false,
    /** "Running numbers" applies to this season (known once its episodes are loaded). */
    val runningNumbersOffered: Boolean = false
)

/** What was picked from the results. */
sealed interface NamingPick {
    data class Series(
        val result: TmdbSeriesResult,
        val plan: NamingGuide.SeriesPlan? = null,
        val isLoading: Boolean = true,
        val failed: Boolean = false,
        /** By TMDB season number; filled from [plan]'s season list, specials last. */
        val sections: Map<Int, NamingSeasonSection> = emptyMap()
    ) : NamingPick {
        /** The seasons in the order the table shows them: 1, 2, … then Specials (season 0). */
        val orderedSections: List<NamingSeasonSection>
            get() = sections.values.sortedWith(compareBy({ it.season == 0 }, { it.season }))
    }

    data class Film(
        val film: TmdbMetadata,
        val plan: NamingGuide.FilmPlan? = null,
        val isLoading: Boolean = true
    ) : NamingPick
}

data class NamingGuideState(
    /** False without a TMDB key: the screen says the helper needs TMDB and searches nothing. */
    val tmdbAvailable: Boolean = true,
    val kind: NamingSearchKind = NamingSearchKind.SERIES,
    val query: String = "",
    val isSearching: Boolean = false,
    /** Null: not searched yet (for the current kind). */
    val seriesResults: List<TmdbSeriesResult>? = null,
    val filmResults: List<TmdbMetadata>? = null,
    val searchFailed: Boolean = false,
    val picked: NamingPick? = null
)

/**
 * The naming helper's search and table (Tasks R.2/R.3, design `docs/design/r0-naming-helper.md` §3, §5), shared by the
 * phone, the TV and the desktop. Every name and every check comes from [guide] (R.1); this only keeps what the
 * screen shows. A search runs [liveSearchDelayMs] after the typing stops (at least [LIVE_SEARCH_MIN_CHARS]
 * characters; each change cancels the search before it), or at once on Search, and a season is fetched when it is
 * first opened, so One Piece costs 1–3 searches + 1 season list + 1–2 checks, and 1 request per opened season.
 *
 * Calls are made from [scope]'s thread (the UI thread on every device): [guide] keeps plain maps.
 */
class NamingGuideModel(
    private val guide: NamingGuide,
    private val scope: CoroutineScope,
    tmdbAvailable: Boolean,
    private val liveSearchDelayMs: Long = LIVE_SEARCH_DELAY_MS
) {
    private val _state = MutableStateFlow(NamingGuideState(tmdbAvailable = tmdbAvailable))
    val state: StateFlow<NamingGuideState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var liveSearchJob: Job? = null
    private var pickJob: Job? = null

    /** The kind and trimmed query of the results shown or on their way; a search for the same asks TMDB nothing. */
    private var searchedFor: Pair<NamingSearchKind, String>? = null

    /**
     * The field's text. Search as you type: [liveSearchDelayMs] after the last change, the query is searched. Below
     * [LIVE_SEARCH_MIN_CHARS] nothing is asked; the results shown stay (no flash while a typo is fixed) until the
     * field is empty.
     */
    fun setQuery(query: String) {
        if (query == _state.value.query) return
        _state.update { it.copy(query = query) }
        liveSearchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length >= LIVE_SEARCH_MIN_CHARS) {
            liveSearchJob = scope.launch {
                delay(liveSearchDelayMs)
                runSearch()
            }
        } else {
            searchJob?.cancel()
            searchedFor = null
            _state.update {
                if (trimmed.isEmpty()) it.copy(isSearching = false, searchFailed = false, seriesResults = null, filmResults = null)
                else it.copy(isSearching = false)
            }
        }
    }

    /** Series or Films; results of the other kind are dropped and the query is searched again as the new kind. */
    fun setKind(kind: NamingSearchKind) {
        if (kind == _state.value.kind) return
        liveSearchJob?.cancel()
        searchJob?.cancel()
        searchedFor = null
        _state.update { it.copy(kind = kind, seriesResults = null, filmResults = null, isSearching = false, searchFailed = false) }
        if (_state.value.query.trim().length >= LIVE_SEARCH_MIN_CHARS) runSearch()
    }

    /** The Search button or the keyboard's Search key: at once, without waiting for the typing to stop. */
    fun search() {
        liveSearchJob?.cancel()
        runSearch()
    }

    private fun runSearch() {
        val current = _state.value
        val query = current.query.trim()
        if (!current.tmdbAvailable || query.isEmpty()) return
        val shownOrComing = current.isSearching || when (current.kind) {
            NamingSearchKind.SERIES -> current.seriesResults != null
            NamingSearchKind.FILMS -> current.filmResults != null
        }
        if (searchedFor == (current.kind to query) && current.picked == null && !current.searchFailed && shownOrComing) return
        searchJob?.cancel()
        pickJob?.cancel()
        searchedFor = current.kind to query
        _state.update { it.copy(isSearching = true, searchFailed = false, picked = null) }
        searchJob = scope.launch {
            when (current.kind) {
                NamingSearchKind.SERIES -> guide.searchSeries(query).fold(
                    onSuccess = { list -> _state.update { it.copy(isSearching = false, seriesResults = list) } },
                    onFailure = { searchedFor = null; _state.update { it.copy(isSearching = false, searchFailed = true) } }
                )
                NamingSearchKind.FILMS -> guide.searchFilms(query).fold(
                    onSuccess = { list -> _state.update { it.copy(isSearching = false, filmResults = list) } },
                    onFailure = { searchedFor = null; _state.update { it.copy(isSearching = false, searchFailed = true) } }
                )
            }
        }
    }

    fun pickSeries(result: TmdbSeriesResult) {
        val results = _state.value.seriesResults.orEmpty()
        // A search still waiting for the typing to stop would close this pick (a search drops the pick).
        liveSearchJob?.cancel()
        pickJob?.cancel()
        _state.update { it.copy(picked = NamingPick.Series(result)) }
        pickJob = scope.launch {
            val plan = guide.seriesPlan(result, results).getOrNull()
            updateSeries(result) { picked ->
                if (plan == null) picked.copy(isLoading = false, failed = true)
                else picked.copy(
                    plan = plan,
                    isLoading = false,
                    sections = plan.seasons.associate { it.seasonNumber to NamingSeasonSection(it.seasonNumber, arcOffered = guide.arcOffered(plan, it.seasonNumber)) }
                )
            }
        }
    }

    fun pickFilm(film: TmdbMetadata) {
        liveSearchJob?.cancel()
        pickJob?.cancel()
        _state.update { it.copy(picked = NamingPick.Film(film)) }
        pickJob = scope.launch {
            val plan = guide.filmPlan(film)
            _state.update { state ->
                val picked = state.picked as? NamingPick.Film
                if (picked?.film?.tmdbId != film.tmdbId) state else state.copy(picked = picked.copy(plan = plan, isLoading = false))
            }
        }
    }

    /** Back from a picked title to the results. */
    fun closePick() {
        pickJob?.cancel()
        _state.update { it.copy(picked = null) }
    }

    /** Opens or closes season [season]; the first opening loads it. */
    fun toggleSeason(season: Int) {
        val section = seriesPick()?.sections?.get(season) ?: return
        if (section.isOpen) {
            updateSection(season) { it.copy(isOpen = false) }
        } else {
            updateSection(season) { it.copy(isOpen = true) }
            if (section.table == null && !section.isLoading) load(season, section.layout)
        }
    }

    /** A layout chip: the season again in [layout] (from the cache, no request after the first). */
    fun setLayout(season: Int, layout: NamingGuide.Layout) {
        val section = seriesPick()?.sections?.get(season) ?: return
        if (section.layout == layout && section.table != null) return
        load(season, layout)
    }

    /** A season that failed to load, again. */
    fun retrySeason(season: Int) {
        val section = seriesPick()?.sections?.get(season) ?: return
        load(season, section.layout)
    }

    private fun load(season: Int, layout: NamingGuide.Layout) {
        val picked = seriesPick() ?: return
        val plan = picked.plan ?: return
        updateSection(season) { it.copy(isLoading = true, failed = false, layout = layout) }
        scope.launch {
            val table = guide.season(plan, season, layout).getOrNull()
            // The episodes are cached by now: no second request (none at all when the season failed).
            val running = table != null &&
                guide.episodes(plan, season).getOrNull()?.let { guide.runningNumbersOffered(plan, season, it) } == true
            updateSeries(picked.result) { current ->
                val section = current.sections[season] ?: return@updateSeries current
                // A later chip press wins over an earlier load still on its way.
                if (section.layout != layout) return@updateSeries current
                current.copy(sections = current.sections + (season to section.copy(
                    isLoading = false,
                    failed = table == null,
                    table = table ?: section.table,
                    runningNumbersOffered = running
                )))
            }
        }
    }

    companion object {
        /** Search as you type: the pause after the last change before the query is searched. */
        const val LIVE_SEARCH_DELAY_MS = 350L

        /** The shortest query searched as you type ("a" finds thousands of titles; Search still asks for it). */
        const val LIVE_SEARCH_MIN_CHARS = 2
    }

    private fun seriesPick(): NamingPick.Series? = _state.value.picked as? NamingPick.Series

    private fun updateSeries(result: TmdbSeriesResult, change: (NamingPick.Series) -> NamingPick.Series) =
        _state.update { state ->
            val picked = state.picked as? NamingPick.Series
            if (picked?.result?.metadata?.tmdbId != result.metadata.tmdbId) state else state.copy(picked = change(picked))
        }

    private fun updateSection(season: Int, change: (NamingSeasonSection) -> NamingSeasonSection) {
        val picked = seriesPick() ?: return
        updateSeries(picked.result) { current ->
            val section = current.sections[season] ?: return@updateSeries current
            current.copy(sections = current.sections + (season to change(section)))
        }
    }
}
