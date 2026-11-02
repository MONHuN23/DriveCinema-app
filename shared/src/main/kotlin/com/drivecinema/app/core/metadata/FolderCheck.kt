package com.drivecinema.app.core.metadata

import java.time.LocalDate

/** A series folder as Drive lists it: its video files, directly in it or in one subfolder level (Task R.1). */
data class SeriesFolderListing(
    val seriesFolderId: String,
    val seriesFolderName: String,
    val files: List<ListedFile>,
    /** The subfolders' names (season folders); a missing one in a suggestion is to be created. */
    val subfolderNames: List<String> = files.mapNotNull { it.seasonFolderName }.distinct()
) {
    data class ListedFile(
        val fileId: String,
        val name: String,
        /** The folder the file is in: the series folder or a season folder. */
        val folderId: String,
        /** Null: directly in the series folder. */
        val seasonFolderName: String?
    )
}

/**
 * The folder check (Task R.1, design `docs/design/r0-naming-helper.md` §3, §6): what the app makes of each file in a
 * series folder, with the sync's own rules ([SeriesFolderSearch], [SeriesLayout], [SeriesEpisodeMatcher]), the
 * problems, a suggested name for each one it can tell, and the aired episodes no file has. Read-only.
 */
class FolderCheck(
    private val tmdb: TmdbClient,
    private val source: TmdbEpisodeSource = CachedTmdbEpisodeSource(tmdb)
) {

    enum class Reason {
        /** The name has no episode number the app can read; it is read as episode 1. */
        NO_EPISODE_NUMBER,
        /** A subfolder without a season number ("Specials", "Extras"), read as season 1. */
        SEASON_FOLDER_WITHOUT_NUMBER,
        /** Read fine, but TMDB has no such episode. */
        NOT_ON_TMDB
    }

    /** A name the app reads as [episode]: [seasonFolder]/[fileName] in the series folder ([seasonFolder] null: directly in it). */
    data class Suggestion(val episode: TmdbEpisode, val seasonFolder: String?, val fileName: String) {
        /** False: the file has to move to another season folder too (R.4 only renames in place). */
        fun inPlace(file: SeriesFolderListing.ListedFile): Boolean = file.seasonFolderName == seasonFolder
    }

    sealed interface FileResult {
        val file: SeriesFolderListing.ListedFile
        val key: EpisodeKey

        data class Matched(override val file: SeriesFolderListing.ListedFile, override val key: EpisodeKey,
                           val episode: TmdbEpisode) : FileResult
        data class NotRecognised(override val file: SeriesFolderListing.ListedFile, override val key: EpisodeKey,
                                 val reason: Reason, val suggestion: Suggestion?) : FileResult
        /** Two or more files for one episode; [episode] is the one they are all read as. */
        data class Duplicate(override val file: SeriesFolderListing.ListedFile, override val key: EpisodeKey,
                             val episode: TmdbEpisode, val otherFiles: List<SeriesFolderListing.ListedFile>,
                             val suggestion: Suggestion?) : FileResult
        /** "Season 01/Show S02E03.mkv": the folder wins (season 1); the file's own season is suggested. */
        data class SeasonMismatch(override val file: SeriesFolderListing.ListedFile, override val key: EpisodeKey,
                                  val readAs: TmdbEpisode?, val fileSays: Int, val suggestion: Suggestion?) : FileResult
    }

    data class Missing(val episode: TmdbEpisode, val suggestion: Suggestion)

    data class Report(
        val listing: SeriesFolderListing,
        /** The show the folder finds, as the sync searches it; null: TMDB finds none (every file is NOT_ON_TMDB). */
        val show: TmdbMetadata?,
        /** The arc season, when the folder is an arc ("Bleach Thousand-Year Blood War (2022)"). */
        val arc: TmdbSeason?,
        val files: List<FileResult>,
        /** Aired episodes of the seasons the folder has files in, which no file is read as. */
        val missing: List<Missing>,
        /** TMDB's season list, for the other seasons' "24 episodes, none on Drive". */
        val seasons: List<TmdbSeason>
    ) {
        val problems: List<FileResult> get() = files.filter { it !is FileResult.Matched }

        /** Season folders that suggestions and missing episodes need and the folder doesn't have yet (for R.4). */
        val foldersToCreate: List<String>
            get() = (files.mapNotNull { suggestionOf(it)?.seasonFolder } + missing.map { it.suggestion.seasonFolder })
                .filterNotNull().distinct().filter { it !in listing.subfolderNames }.sorted()

        private fun suggestionOf(result: FileResult): Suggestion? = when (result) {
            is FileResult.Matched -> null
            is FileResult.NotRecognised -> result.suggestion
            is FileResult.Duplicate -> result.suggestion
            is FileResult.SeasonMismatch -> result.suggestion
        }
    }

    suspend fun check(listing: SeriesFolderListing, today: LocalDate = LocalDate.now()): Result<Report> {
        val found = SeriesFolderSearch.findFolder(tmdb, listing.seriesFolderName).getOrElse { return Result.failure(it) }
        val keys = listing.files.associateWith { SeriesLayout.episodeKey(it.seasonFolderName, it.name) }
        if (found == null) {
            val files = keys.map { (file, key) -> FileResult.NotRecognised(file, key, Reason.NOT_ON_TMDB, null) }
            return Result.success(Report(listing, null, null, files, emptyList(), emptyList()))
        }
        val tvId = found.show.tmdbId
        // The file name alone ("Show S02E03" without its folder): where a misplaced file belongs.
        val alone = keys.mapValues { (file, key) ->
            if (file.seasonFolderName == null || key.pattern == EpisodeNamePattern.NONE) null
            else SeriesLayout.episodeKey(null, file.name)
        }
        val wanted = (keys.values.map { it.pair } + alone.values.mapNotNull { it?.pair }).distinct()
        val match = SeriesEpisodeMatcher(source).match(tvId, found.show.title, found.searchTitle, wanted)
        val seasons = source.seasons(tvId).getOrElse { emptyList() }
        // A folder of fansub names ("One Piece - 1171") gets fansub suggestions, in the folder most of them are in.
        val running = keys.entries.filter { it.value.pattern == EpisodeNamePattern.RUNNING_NUMBER }
        val fansubFolder = if (match.arc == null && running.size * 2 > keys.size) {
            running.groupingBy { it.key.seasonFolderName }.eachCount().maxByOrNull { it.value }?.let { Folder(it.key) }
        } else null
        val namer = Namer(found, match.arc, fansubFolder, seasons)

        val byEpisode = keys.entries
            .filter { (_, key) -> key.pattern != EpisodeNamePattern.NONE }
            .mapNotNull { (file, key) -> match.episodes[key.pair]?.let { it to file } }
            .groupBy({ it.first }, { it.second })

        suspend fun suggestionFor(file: SeriesFolderListing.ListedFile): Suggestion? {
            val episode = alone[file]?.let { match.episodes[it.pair] } ?: return null
            if (byEpisode[episode].orEmpty().any { it != file }) return null // another file already is that episode
            return namer.suggest(episode, LibraryNames.extensionOf(file.name) ?: "mkv")
        }

        val results = keys.map { (file, key) ->
            val episode = match.episodes[key.pair]
            val fileSays = key.fileSeason
            when {
                key.pattern == EpisodeNamePattern.NONE ->
                    FileResult.NotRecognised(file, key, Reason.NO_EPISODE_NUMBER, null)
                episode == null -> FileResult.NotRecognised(
                    file, key,
                    if (key.seasonFolderWithoutNumber) Reason.SEASON_FOLDER_WITHOUT_NUMBER else Reason.NOT_ON_TMDB,
                    suggestionFor(file)
                )
                match.arc == null && file.seasonFolderName != null && fileSays != null && fileSays != key.season ->
                    FileResult.SeasonMismatch(file, key, episode, fileSays, suggestionFor(file))
                byEpisode.getValue(episode).size > 1 ->
                    FileResult.Duplicate(file, key, episode, byEpisode.getValue(episode) - file, suggestionFor(file))
                else -> FileResult.Matched(file, key, episode)
            }
        }

        // Missing: aired episodes of the TMDB seasons the folder's files are read into.
        val haveSeasons = if (match.arc != null) setOf(match.arc.seasonNumber)
        else byEpisode.keys.map { it.seasonNumber }.toSet()
        val have = byEpisode.keys.map { it.seasonNumber to it.episodeNumber }.toSet()
        val missing = haveSeasons.sorted().flatMap { season ->
            source.episodes(tvId, season).getOrElse { emptyList() }
                .filter { (it.seasonNumber to it.episodeNumber) !in have && aired(it, today) }
                .sortedBy { it.episodeNumber }
                .map { Missing(it, namer.suggest(it, commonExtension(listing))) }
        }
        return Result.success(Report(listing, found.show, match.arc, results, missing, seasons))
    }

    private fun aired(episode: TmdbEpisode, today: LocalDate): Boolean =
        episode.airDate?.let { runCatching { !LocalDate.parse(it).isAfter(today) }.getOrNull() } ?: false

    private fun commonExtension(listing: SeriesFolderListing): String =
        listing.files.mapNotNull { LibraryNames.extensionOf(it.name)?.lowercase() }
            .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: "mkv"

    private data class Folder(val name: String?)

    /**
     * Names in the folder's own layout: the show's seasons; the arc's broadcast parts for an arc folder; running
     * numbers in [fansub]'s folder for a fansub folder (only when TMDB numbers the episode the same way).
     */
    private inner class Namer(
        found: SeriesFolderSearch.Found,
        private val arc: TmdbSeason?,
        private val fansub: Folder?,
        private val seasons: List<TmdbSeason>
    ) {
        private val tvId = found.show.tmdbId
        private val title = if (arc != null) LibraryNames.arcTitle(found.show.title, arc.name)
        else LibraryNames.clean(found.show.title)
        private var parts: List<List<TmdbEpisode>>? = null

        /** [episode]'s number counted through the seasons, as [TmdbEpisodeMapping.absoluteEpisode] counts. */
        private suspend fun runningNumber(episode: TmdbEpisode): Int? {
            if (episode.seasonNumber < 1) return null
            val position = source.episodes(tvId, episode.seasonNumber).getOrElse { return null }
                .sortedBy { it.episodeNumber }.indexOfFirst { it.episodeNumber == episode.episodeNumber }
            if (position < 0) return null
            return seasons.filter { it.seasonNumber in 1 until episode.seasonNumber }.sumOf { it.episodeCount } + position + 1
        }

        suspend fun suggest(episode: TmdbEpisode, extension: String): Suggestion {
            if (arc != null && episode.seasonNumber == arc.seasonNumber) {
                val split = parts ?: TmdbEpisodeMapping.broadcastParts(
                    source.episodes(tvId, arc.seasonNumber).getOrElse { emptyList() }
                ).also { parts = it }
                split.forEachIndexed { part, list ->
                    val index = list.indexOfFirst { it.episodeNumber == episode.episodeNumber }
                    if (index >= 0) return Suggestion(
                        episode, LibraryNames.seasonFolder(part + 1),
                        LibraryNames.episodeFile(title, part + 1, index + 1, extension)
                    )
                }
            }
            if (fansub != null && runningNumber(episode) == episode.episodeNumber) {
                return Suggestion(episode, fansub.name, LibraryNames.fansubFile(title, episode.episodeNumber, extension))
            }
            return Suggestion(
                episode, LibraryNames.seasonFolder(episode.seasonNumber),
                LibraryNames.episodeFile(title, episode.seasonNumber, episode.episodeNumber, extension)
            )
        }
    }
}
