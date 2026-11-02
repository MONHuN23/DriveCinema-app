package com.drivecinema.app.core.naming

import com.drivecinema.app.core.metadata.CachedTmdbEpisodeSource
import com.drivecinema.app.core.metadata.FolderCheck
import com.drivecinema.app.core.metadata.FolderCheck.FileResult
import com.drivecinema.app.core.metadata.LibraryNames
import com.drivecinema.app.core.metadata.NamingGuide
import com.drivecinema.app.core.metadata.SeriesFolderListing
import com.drivecinema.app.core.metadata.TmdbClient
import com.drivecinema.app.core.metadata.TmdbSeason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** The folder check's filter (R.0 §3): every row, the files with a problem, or the aired episodes no file has. */
enum class NamingCheckFilter { ALL, PROBLEMS, MISSING }

/** One line of a season in the folder check. */
sealed interface NamingCheckRow {
    /** A file of the folder, placed in this season. */
    data class File(val result: FileResult) : NamingCheckRow
    /** An aired episode no file is read as, with the name to use. */
    data class Missing(val missing: FolderCheck.Missing) : NamingCheckRow
    /** An episode of a season the folder has no file in, from the naming guide (shown once the season is opened). */
    data class Suggested(val row: NamingGuide.Row) : NamingCheckRow

    val isProblem: Boolean get() = this is File && result !is FileResult.Matched
    val isMissing: Boolean get() = this is Missing || this is Suggested
}

/** A TMDB season in the folder check: open with its rows when the folder has files in it, otherwise closed. */
data class NamingCheckSeason(
    val season: Int,
    val title: String,
    val episodeCount: Int,
    val hasFiles: Boolean,
    val rows: List<NamingCheckRow> = emptyList(),
    val isOpen: Boolean = hasFiles,
    val isLoading: Boolean = false,
    val failed: Boolean = false
) {
    fun rows(filter: NamingCheckFilter): List<NamingCheckRow> = when (filter) {
        NamingCheckFilter.ALL -> rows
        NamingCheckFilter.PROBLEMS -> rows.filter { it.isProblem }
        // Only the seasons with files: the others' episodes are all "missing" and would bury the real gaps.
        NamingCheckFilter.MISSING -> if (hasFiles) rows.filter { it.isMissing } else emptyList()
    }
}

sealed interface NamingCheckState {
    data object Loading : NamingCheckState
    data object NoTmdb : NamingCheckState
    /** A series from a phone, TV or computer folder: the check reads Drive folders only (R.0 §3). */
    data object LocalSeries : NamingCheckState
    /** [drive]: the Drive folder couldn't be read; otherwise TMDB couldn't be asked. */
    data class Failed(val drive: Boolean) : NamingCheckState
    data class Ready(
        val report: FolderCheck.Report,
        /** Files the app can't place in any TMDB season (no number, a season TMDB doesn't have, no show found). */
        val unplaced: List<FileResult>,
        val seasons: List<NamingCheckSeason>,
        val filter: NamingCheckFilter = NamingCheckFilter.ALL,
        /** The user's own answer for a file without a suggestion: "this file is that missing episode" (by file id). */
        val picks: Map<String, FolderCheck.Missing> = emptyMap()
    ) : NamingCheckState {
        val problemCount: Int get() = report.problems.size
        val missingCount: Int get() = report.missing.size

        /** The suggestion a row shows for [result]: the check's own, else the user's pick. */
        fun suggestionFor(result: FileResult): FolderCheck.Suggestion? = when (result) {
            is FileResult.Matched -> null
            is FileResult.NotRecognised -> result.suggestion
            is FileResult.Duplicate -> result.suggestion
            is FileResult.SeasonMismatch -> result.suggestion
        } ?: picks[result.file.fileId]?.suggestion

        /** Whether [result] can be given an episode by hand: a problem without a suggestion, and episodes to pick from. */
        fun canPick(result: FileResult): Boolean = result !is FileResult.Matched && report.missing.isNotEmpty() &&
            when (result) {
                is FileResult.NotRecognised -> result.suggestion == null
                is FileResult.Duplicate -> result.suggestion == null
                is FileResult.SeasonMismatch -> result.suggestion == null
                is FileResult.Matched -> false
            }
    }
}

/**
 * The series page's Naming check (Tasks R.2/R.3, design R.0 §3): one Drive series folder through R.1's [FolderCheck],
 * arranged as TMDB's seasons with the user's files and the missing episodes. Read-only: it lists the folder and asks
 * TMDB, nothing else. Seasons without files are closed and loaded when opened, with the naming guide's names.
 *
 * [listing] reads the series folder on Drive (the platform brings the token and the HTTP client); it throws when
 * Drive can't be read.
 */
class NamingCheckModel(
    private val scope: CoroutineScope,
    tmdb: TmdbClient,
    private val tmdbAvailable: Boolean,
    private val isDriveSeries: Boolean,
    private val listing: suspend () -> SeriesFolderListing,
    private val today: () -> LocalDate = LocalDate::now
) {
    // One cache for the check and the closed seasons' names: a season the check fetched is never fetched again.
    private val source = CachedTmdbEpisodeSource(tmdb)
    private val check = FolderCheck(tmdb, source)
    private val guide = NamingGuide(tmdb, source)

    private val _state = MutableStateFlow<NamingCheckState>(NamingCheckState.Loading)
    val state: StateFlow<NamingCheckState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** Reads the folder and checks it (again, for Retry or after a change on Drive). */
    fun load() {
        if (!tmdbAvailable) {
            _state.value = NamingCheckState.NoTmdb
            return
        }
        if (!isDriveSeries) {
            _state.value = NamingCheckState.LocalSeries
            return
        }
        loadJob?.cancel()
        _state.value = NamingCheckState.Loading
        loadJob = scope.launch {
            val folder = try {
                listing()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = NamingCheckState.Failed(drive = true)
                return@launch
            }
            _state.value = check.check(folder, today()).fold(
                onSuccess = { ready(it) },
                onFailure = { NamingCheckState.Failed(drive = false) }
            )
        }
    }

    fun setFilter(filter: NamingCheckFilter) = updateReady { it.copy(filter = filter) }

    /** The user says file [fileId] is [missing]'s episode; null clears it. */
    fun pick(fileId: String, missing: FolderCheck.Missing?) = updateReady { ready ->
        ready.copy(picks = if (missing == null) ready.picks - fileId else ready.picks + (fileId to missing))
    }

    /** Opens or closes a season; a season without files is loaded the first time. */
    fun toggleSeason(season: Int) {
        val ready = _state.value as? NamingCheckState.Ready ?: return
        val section = ready.seasons.firstOrNull { it.season == season } ?: return
        updateSeason(season) { it.copy(isOpen = !it.isOpen) }
        if (!section.isOpen && !section.hasFiles && section.rows.isEmpty() && !section.isLoading) loadSeason(ready.report, season)
    }

    private fun loadSeason(report: FolderCheck.Report, season: Int) {
        val show = report.show ?: return
        // The folder's own name and the sync's title, so the names fit next to the files already there.
        val plan = NamingGuide.SeriesPlan(
            show = show,
            folderName = report.listing.seriesFolderName,
            fileTitle = LibraryNames.clean(show.title),
            folderFindsShow = true,
            seasons = report.seasons
        )
        updateSeason(season) { it.copy(isLoading = true, failed = false) }
        scope.launch {
            val table = guide.season(plan, season, NamingGuide.Layout.SHOW_SEASON).getOrNull()
            updateSeason(season) { section ->
                section.copy(
                    isLoading = false,
                    failed = table == null,
                    // Rows that don't read back stay: the screen shows a warning instead of their name.
                    rows = table?.rows?.map { NamingCheckRow.Suggested(it) } ?: section.rows
                )
            }
        }
    }

    private fun updateReady(change: (NamingCheckState.Ready) -> NamingCheckState.Ready) =
        _state.update { if (it is NamingCheckState.Ready) change(it) else it }

    private fun updateSeason(season: Int, change: (NamingCheckSeason) -> NamingCheckSeason) = updateReady { ready ->
        ready.copy(seasons = ready.seasons.map { if (it.season == season) change(it) else it })
    }

    companion object {
        /** The check's report as the screen shows it: unplaced files first, then TMDB's seasons (specials last). */
        fun ready(report: FolderCheck.Report): NamingCheckState.Ready {
            val placed = mutableMapOf<Int, MutableList<Pair<Int, NamingCheckRow>>>()
            val unplaced = mutableListOf<FileResult>()
            for (result in report.files) {
                val episode = when (result) {
                    is FileResult.Matched -> result.episode
                    is FileResult.Duplicate -> result.episode
                    is FileResult.SeasonMismatch -> result.readAs
                    is FileResult.NotRecognised -> null
                }
                if (episode == null) unplaced += result
                else placed.getOrPut(episode.seasonNumber) { mutableListOf() } += episode.episodeNumber to NamingCheckRow.File(result)
            }
            for (missing in report.missing) {
                placed.getOrPut(missing.episode.seasonNumber) { mutableListOf() } +=
                    missing.episode.episodeNumber to NamingCheckRow.Missing(missing)
            }
            // An arc folder is one TMDB season; other seasons don't belong in it.
            val tmdbSeasons: List<TmdbSeason> = when {
                report.arc != null -> listOf(report.arc)
                else -> report.seasons
            }
            val known = tmdbSeasons.map { it.seasonNumber }.toSet()
            val seasons = (tmdbSeasons.map { it.seasonNumber to it } + placed.keys.filter { it !in known }.map { it to null })
                .sortedWith(compareBy({ it.first == 0 }, { it.first }))
                .map { (number, tmdbSeason) ->
                    val rows = placed[number].orEmpty().sortedBy { it.first }.map { it.second }
                    NamingCheckSeason(
                        season = number,
                        title = NamingTexts.seasonTitle(number, tmdbSeason?.name),
                        episodeCount = tmdbSeason?.episodeCount ?: rows.size,
                        hasFiles = rows.any { it is NamingCheckRow.File },
                        rows = rows
                    )
                }
            return NamingCheckState.Ready(report, unplaced, seasons)
        }
    }
}
