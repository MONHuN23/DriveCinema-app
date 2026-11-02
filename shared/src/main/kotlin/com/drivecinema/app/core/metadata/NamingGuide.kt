package com.drivecinema.app.core.metadata

/**
 * The naming guide (Task R.1, design `docs/design/r0-naming-helper.md` §3–§5): search TMDB, pick a series or a film,
 * and get the names the app recognises. Every name is read back before it is offered, with the sync's own code:
 * the series folder with [SeriesFolderSearch], each file with [SeriesLayout] and [SeriesEpisodeMatcher].
 *
 * One object per screen: TMDB's seasons are fetched when a season is opened and kept in [source] for the screen's
 * lifetime, never all at once (One Piece has about 1,180 episodes in 23 seasons).
 */
class NamingGuide(
    private val tmdb: TmdbClient,
    private val source: TmdbEpisodeSource = CachedTmdbEpisodeSource(tmdb)
) {

    /** How a season's files are laid out (user, 2026-11-01: [SHOW_SEASON] by default, the others as options). */
    enum class Layout {
        /** `Show (Year)/Season 02/Show S02E14.mkv`, TMDB's season and episode numbers. */
        SHOW_SEASON,
        /** The arc as its own show: `Show Arc (Year)/Season 01…`, one season per broadcast part. */
        ARC_FOLDER,
        /** Fansub style: `Show (Year)/Season 01/Show - 1086.mkv`, a running number. */
        RUNNING_NUMBER
    }

    data class SeriesPlan(
        val show: TmdbMetadata,
        /** "One Piece (Anime) (1999)". */
        val folderName: String,
        /** What the episode files start with: TMDB's name, cleaned ("One Piece"). */
        val fileTitle: String,
        /** The sync's search for [folderName] finds [show]: true, another or no show: false, unknown (request failed): null. */
        val folderFindsShow: Boolean?,
        /** TMDB's season list (specials are season 0); episode counts fill the closed season groups. */
        val seasons: List<TmdbSeason>
    )

    data class Row(
        val episode: TmdbEpisode,
        val seriesFolder: String,
        val seasonFolder: String,
        val fileName: String,
        /** The app reads [seasonFolder]/[fileName] back as [episode]. False: show a warning, not the name. */
        val readsBack: Boolean
    ) {
        /** "Series/Breaking Bad (2008)/Season 01/Breaking Bad S01E01.mkv". */
        val path: String get() = "${LibraryNames.SERIES_FOLDER}/$seriesFolder/$seasonFolder/$fileName"
    }

    data class SeasonTable(
        val season: Int,
        val layout: Layout,
        val seriesFolder: String,
        /** For [Layout.ARC_FOLDER], whether that folder finds the show (same meaning as [SeriesPlan.folderFindsShow]). */
        val folderFindsShow: Boolean?,
        val rows: List<Row>
    )

    data class FilmPlan(
        val film: TmdbMetadata,
        /** "Ted (2012).mkv", for the Films folder. */
        val fileName: String,
        /** The app's own film search reads [fileName] and takes TMDB's first result: is it [film]? Null: unknown. */
        val appFindsIt: Boolean?,
        /** The film the app would pick instead, when [appFindsIt] is false. */
        val appPicks: TmdbMetadata? = null
    )

    suspend fun searchSeries(query: String, year: Int? = null): Result<List<TmdbSeriesResult>> =
        tmdb.searchSeriesCandidates(query, year)

    suspend fun searchFilms(query: String, year: Int? = null): Result<List<TmdbMetadata>> =
        tmdb.searchMovieCandidates(query, year)

    private val folderChecks = mutableMapOf<String, Boolean?>()

    /** Whether the sync's search for the series folder [folderName] finds show [tvId]; cached per name. */
    private suspend fun folderFinds(folderName: String, tvId: Int): Boolean? =
        folderChecks.getOrPut(folderName) {
            SeriesFolderSearch.findFolder(tmdb, folderName).getOrElse { return null }?.show?.tmdbId == tvId
        }

    /** The series folder for [chosen] (one of [results], the list it was picked from) and its season list. */
    suspend fun seriesPlan(chosen: TmdbSeriesResult, results: List<TmdbSeriesResult>): Result<SeriesPlan> {
        val show = chosen.metadata
        val folder = seriesFolderName(chosen, results)
        val seasons = source.seasons(show.tmdbId).getOrElse { return Result.failure(it) }
        return Result.success(
            SeriesPlan(show, folder, LibraryNames.clean(show.title), folderFinds(folder, show.tmdbId), seasons)
        )
    }

    /** Whether season [season] can be its own arc folder ([TmdbEpisodeMapping.arcSeason]'s rules). */
    fun arcOffered(plan: SeriesPlan, season: Int): Boolean {
        val tmdbSeason = plan.seasons.firstOrNull { it.seasonNumber == season } ?: return false
        if (season < 1) return false
        val arcTitle = LibraryNames.arcTitle(plan.show.title, tmdbSeason.name)
        return TmdbEpisodeMapping.arcSeason(plan.seasons, arcTitle, plan.show.title)?.seasonNumber == season
    }

    /**
     * Whether season [season] can use running numbers: TMDB numbers its episodes through the seasons (One Piece's
     * season 2 starts at 62). Not offered for season 1 (its names are the same either way) or specials.
     */
    fun runningNumbersOffered(plan: SeriesPlan, season: Int, episodes: List<TmdbEpisode>): Boolean {
        if (season < 2) return false
        val before = runningBefore(plan, season)
        return before > 0 && episodes.minOfOrNull { it.episodeNumber } == before + 1
    }

    private fun runningBefore(plan: SeriesPlan, season: Int) =
        plan.seasons.filter { it.seasonNumber in 1 until season }.sumOf { it.episodeCount }

    /** TMDB's episodes of [season] (one request the first time). */
    suspend fun episodes(plan: SeriesPlan, season: Int): Result<List<TmdbEpisode>> =
        source.episodes(plan.show.tmdbId, season).map { list -> list.sortedBy { it.episodeNumber } }

    /** The rows of season [season] in [layout], each with its name and whether the app reads it back. */
    suspend fun season(plan: SeriesPlan, season: Int, layout: Layout, extension: String = "mkv"): Result<SeasonTable> {
        val episodes = episodes(plan, season).getOrElse { return Result.failure(it) }
        val tvId = plan.show.tmdbId
        val used = when {
            layout == Layout.ARC_FOLDER && arcOffered(plan, season) -> Layout.ARC_FOLDER
            layout == Layout.RUNNING_NUMBER && runningNumbersOffered(plan, season, episodes) -> Layout.RUNNING_NUMBER
            else -> Layout.SHOW_SEASON
        }
        val folderOf = mutableMapOf<TmdbEpisode, Pair<String, String>>() // episode -> (season folder, file name)
        val seriesFolder: String
        var folderFindsShow = plan.folderFindsShow
        when (used) {
            Layout.ARC_FOLDER -> {
                val arc = plan.seasons.first { it.seasonNumber == season }
                val arcTitle = LibraryNames.arcTitle(plan.show.title, arc.name)
                val year = (arc.airDate ?: episodes.firstOrNull()?.airDate)?.take(4)?.toIntOrNull()
                seriesFolder = LibraryNames.seriesFolder(arcTitle, year, animeTag = false)
                folderFindsShow = folderFinds(seriesFolder, tvId)
                TmdbEpisodeMapping.broadcastParts(episodes).forEachIndexed { part, partEpisodes ->
                    partEpisodes.forEachIndexed { index, episode ->
                        folderOf[episode] = LibraryNames.seasonFolder(part + 1) to
                            LibraryNames.episodeFile(arcTitle, part + 1, index + 1, extension)
                    }
                }
            }
            Layout.RUNNING_NUMBER -> {
                seriesFolder = plan.folderName
                val before = runningBefore(plan, season)
                episodes.forEachIndexed { index, episode ->
                    folderOf[episode] = LibraryNames.seasonFolder(1) to
                        LibraryNames.fansubFile(plan.fileTitle, before + index + 1, extension)
                }
            }
            Layout.SHOW_SEASON -> {
                seriesFolder = plan.folderName
                for (episode in episodes) {
                    folderOf[episode] = LibraryNames.seasonFolder(season) to
                        LibraryNames.episodeFile(plan.fileTitle, season, episode.episodeNumber, extension)
                }
            }
        }
        val readBack = readBack(plan, seriesFolder, folderOf.values)
        val rows = episodes.map { episode ->
            val (seasonFolder, file) = folderOf.getValue(episode)
            val back = readBack[seasonFolder to file]
            Row(episode, seriesFolder, seasonFolder, file, back?.seasonNumber == season && back.episodeNumber == episode.episodeNumber)
        }
        return Result.success(SeasonTable(season, used, seriesFolder, folderFindsShow, rows))
    }

    /**
     * What the sync makes of each (season folder, file) in [seriesFolder]: [SeriesLayout] reads the names and
     * [SeriesEpisodeMatcher] finds the TMDB episodes, from the seasons this screen has loaded (more only if needed).
     */
    private suspend fun readBack(
        plan: SeriesPlan,
        seriesFolder: String,
        names: Collection<Pair<String, String>>
    ): Map<Pair<String, String>, TmdbEpisode> {
        val folderTitle = MediaFilenameParser.parseMovie(seriesFolder).cleanTitle
        val searchTitle = TmdbSeriesMatcher.withoutAnimeTag(folderTitle) ?: folderTitle
        val keys = names.associateWith { (seasonFolder, file) -> SeriesLayout.episodeKey(seasonFolder, file).pair }
        val match = SeriesEpisodeMatcher(source).match(plan.show.tmdbId, plan.show.title, searchTitle, keys.values.distinct())
        return keys.mapNotNull { (name, key) -> match.episodes[key]?.let { name to it } }.toMap()
    }

    /** The Films name for [film] and whether the app's film search, given that name, finds it. */
    suspend fun filmPlan(film: TmdbMetadata, extension: String = "mkv"): FilmPlan {
        val fileName = filmPreview(film, extension)
        val parsed = MediaFilenameParser.parseMovie(fileName)
        val picked = tmdb.searchMovie(parsed.cleanTitle, parsed.year).getOrElse { return FilmPlan(film, fileName, null) }
        val finds = picked?.tmdbId == film.tmdbId
        return FilmPlan(film, fileName, finds, appPicks = picked.takeIf { !finds })
    }

    /** A series result's names before it is picked: its folder and its first episode's season folder and file. */
    data class SeriesPreview(val folderName: String, val seasonFolder: String, val fileName: String)

    companion object {
        /**
         * The TV's names panel (UI task ui/tv-browse-naming): the names [seriesPlan] and [season] give [chosen]'s
         * first episode, without TMDB and not read back, so moving the focus through the results asks nothing.
         */
        fun seriesPreview(chosen: TmdbSeriesResult, results: List<TmdbSeriesResult>, extension: String = "mkv") =
            SeriesPreview(
                folderName = seriesFolderName(chosen, results),
                seasonFolder = LibraryNames.seasonFolder(1),
                fileName = LibraryNames.episodeFile(chosen.metadata.title, 1, 1, extension)
            )

        /** The Films name [filmPlan] gives [film], without TMDB (its check comes with the pick). */
        fun filmPreview(film: TmdbMetadata, extension: String = "mkv"): String =
            LibraryNames.filmFile(film.title, film.year, extension)

        private fun seriesFolderName(chosen: TmdbSeriesResult, results: List<TmdbSeriesResult>): String =
            LibraryNames.seriesFolder(chosen.metadata.title, chosen.metadata.year, LibraryNames.needsAnimeTag(chosen, results))
    }
}
