package com.drivecinema.app.core.metadata

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Where a folder's episodes live on TMDB when its season numbers are not TMDB's. TMDB has no show for
 * "Bleach: Thousand-Year Blood War": it is season 2 of "Bleach", 50 episodes in four broadcast parts. So the folder
 * "Bleach: Thousand-Year Blood War (2022)/Season 01" means that season's first part, not Bleach's first season.
 */
object TmdbEpisodeMapping {

    /** A gap this long between two episodes starts a new broadcast part (cour) of a season. */
    private const val PART_GAP_DAYS = 60

    /** Season names that say nothing about an arc: "Season 2", "Specials". */
    private val GENERIC_SEASON = Regex("""(season|series|part|volume|book)\d*|specials?|extras?""")

    /**
     * The TMDB season whose name appears in [folderTitle], when the folder names an arc of [showName] rather than
     * the show itself; the longest such name wins. Null when the folder is the show ("Grey's Anatomy" ==
     * "Greys Anatomy") or no season has a specific name in it. A season named like the show (Bleach's season 1 is
     * "Bleach") is not an arc.
     */
    fun arcSeason(seasons: List<TmdbSeason>, folderTitle: String, showName: String): TmdbSeason? {
        val folder = TmdbSeriesMatcher.normalize(folderTitle)
        val show = TmdbSeriesMatcher.normalize(showName)
        if (folder == show) return null
        return seasons
            .filter { it.seasonNumber >= 1 }
            .map { it to TmdbSeriesMatcher.normalize(it.name) }
            .filter { (_, name) ->
                name.length >= 4 && name != show && !GENERIC_SEASON.matches(name) && folder.contains(name)
            }
            .maxByOrNull { (_, name) -> name.length }
            ?.first
    }

    /** [episodes] cut wherever more than [PART_GAP_DAYS] pass between two air dates. */
    fun broadcastParts(episodes: List<TmdbEpisode>): List<List<TmdbEpisode>> {
        val parts = mutableListOf<MutableList<TmdbEpisode>>()
        var lastAired: LocalDate? = null
        for (episode in episodes.sortedBy { it.episodeNumber }) {
            val aired = episode.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val newPart = parts.isEmpty() ||
                (aired != null && lastAired != null && ChronoUnit.DAYS.between(lastAired, aired) > PART_GAP_DAYS)
            if (newPart) parts += mutableListOf<TmdbEpisode>()
            parts.last() += episode
            if (aired != null) lastAired = aired
        }
        return parts
    }

    /**
     * The arc's episode for the folder's "Season [season]" episode [episode]. Season N is the arc's Nth part, as
     * streaming services number it ("Season 02" episode 1 = episode 14 of TMDB's season); a number past the end
     * of that part is the arc's own numbering ("Season 01" episode 27 = episode 27).
     */
    fun arcEpisode(parts: List<List<TmdbEpisode>>, season: Int, episode: Int): TmdbEpisode? {
        if (season < 1 || episode < 1) return null
        parts.getOrNull(season - 1)?.getOrNull(episode - 1)?.let { return it }
        return parts.flatten().firstOrNull { it.episodeNumber == episode }
    }

    /**
     * Where running episode number [episode] falls when the show's seasons (specials left out) are counted one
     * after another: (TMDB season, 0-based position in it), or null past the last one. Bleach's 367th episode is
     * the first of season 2 ("Thousand-Year Blood War"), after season 1's 366.
     */
    fun absoluteEpisode(seasons: List<TmdbSeason>, episode: Int): Pair<Int, Int>? {
        if (episode < 1) return null
        var before = 0
        for (s in seasons.filter { it.seasonNumber >= 1 && it.episodeCount > 0 }.sortedBy { it.seasonNumber }) {
            if (episode <= before + s.episodeCount) return s.seasonNumber to (episode - before - 1)
            before += s.episodeCount
        }
        return null
    }
}

/**
 * Where [SeriesEpisodeMatcher] reads a show's seasons: TMDB itself ([TmdbClient]), or [CachedTmdbEpisodeSource], which
 * the naming helper shares between its table and its read-back checks (Task R.1), so a season is fetched once.
 */
interface TmdbEpisodeSource {
    suspend fun seasons(tvId: Int): Result<List<TmdbSeason>>
    suspend fun episodes(tvId: Int, season: Int): Result<List<TmdbEpisode>>
    suspend fun tvdbOrder(tvId: Int): Result<Map<Int, List<TmdbEpisode>>>
}

/** [tmdb]'s answers, kept for this object's lifetime (one screen); only successes are kept, so a failure is retried. */
class CachedTmdbEpisodeSource(private val tmdb: TmdbEpisodeSource) : TmdbEpisodeSource {
    private val seasonLists = mutableMapOf<Int, List<TmdbSeason>>()
    private val seasonEpisodes = mutableMapOf<Pair<Int, Int>, List<TmdbEpisode>>()
    private val tvdb = mutableMapOf<Int, Map<Int, List<TmdbEpisode>>>()

    override suspend fun seasons(tvId: Int): Result<List<TmdbSeason>> =
        seasonLists[tvId]?.let { Result.success(it) } ?: tmdb.seasons(tvId).onSuccess { seasonLists[tvId] = it }

    override suspend fun episodes(tvId: Int, season: Int): Result<List<TmdbEpisode>> =
        seasonEpisodes[tvId to season]?.let { Result.success(it) }
            ?: tmdb.episodes(tvId, season).onSuccess { seasonEpisodes[tvId to season] = it }

    override suspend fun tvdbOrder(tvId: Int): Result<Map<Int, List<TmdbEpisode>>> =
        tvdb[tvId]?.let { Result.success(it) } ?: tmdb.tvdbOrder(tvId).onSuccess { tvdb[tvId] = it }
}

/**
 * Finds TMDB's data for a series folder's episodes, given as (season, episode) pairs. As before, folder season N
 * is TMDB season N. Fallbacks, each tried only when that doesn't fit:
 * - The folder names an arc that TMDB files as a season ([TmdbEpisodeMapping.arcSeason]); one extra request.
 * - A folder season TMDB doesn't have ("Bleach/Season 17"): the show's TVDB order is used, when TMDB has one.
 * - A running episode number still unmatched ("One Piece - 1171"): [TmdbEpisodeMapping.absoluteEpisode].
 */
class SeriesEpisodeMatcher(private val tmdb: TmdbEpisodeSource) {

    data class Match(
        val episodes: Map<Pair<Int, Int>, TmdbEpisode>,
        /** The arc's season, when the folder is one; its poster and overview fit better than the show's. */
        val arc: TmdbSeason?
    )

    suspend fun match(tvId: Int, showName: String, folderTitle: String, local: List<Pair<Int, Int>>): Match {
        val found = mutableMapOf<Pair<Int, Int>, TmdbEpisode>()
        val seasons = local.map { it.first }.distinct()

        var seasonList: List<TmdbSeason>? = null
        suspend fun seasonList(): List<TmdbSeason> =
            seasonList ?: tmdb.seasons(tvId).getOrNull().orEmpty().also { seasonList = it }
        val fetched = mutableMapOf<Int, List<TmdbEpisode>?>()
        suspend fun episodesOf(season: Int): List<TmdbEpisode>? =
            fetched.getOrPut(season) { tmdb.episodes(tvId, season).getOrNull() }

        val arc = if (TmdbSeriesMatcher.normalize(folderTitle) == TmdbSeriesMatcher.normalize(showName)) null
        else TmdbEpisodeMapping.arcSeason(seasonList(), folderTitle, showName)
        if (arc != null) {
            val parts = TmdbEpisodeMapping.broadcastParts(episodesOf(arc.seasonNumber).orEmpty())
            for ((season, episode) in local) {
                TmdbEpisodeMapping.arcEpisode(parts, season, episode)?.let { found[season to episode] = it }
            }
        }

        // Folder season N = TMDB season N; in an arc folder, only the specials (season 0) are left.
        val bySeason = seasons.filter { arc == null || it < 1 }
        val missing = mutableSetOf<Int>()
        for (season in bySeason) {
            val episodes = episodesOf(season)
            if (episodes.isNullOrEmpty()) {
                missing += season
                continue
            }
            val byNumber = episodes.associateBy { it.episodeNumber }
            for ((s, episode) in local) {
                if (s == season) byNumber[episode]?.let { found[s to episode] = it }
            }
        }

        // A season TMDB doesn't have means the folders follow TVDB, so TVDB's order wins for every season it has.
        if (missing.isNotEmpty()) {
            val tvdb = tmdb.tvdbOrder(tvId).getOrNull().orEmpty()
            if (missing.any { it in tvdb }) {
                for ((season, episode) in local) {
                    if (season !in bySeason) continue
                    tvdb[season]?.getOrNull(episode - 1)?.let { found[season to episode] = it }
                }
            }
        }

        // Running numbers ("One Piece - 1171" is read as season 1; "S24E1180" is a season TMDB lacks), counted
        // through the seasons. Kept in season 1 only when TMDB numbers that episode the same way (One Piece does),
        // in a missing season only when no season is that long; so a normal show's episode TMDB doesn't list
        // yet ("S01E14" of 13, "S06E03") stays unmatched instead of getting another episode's title.
        val unmatched = local.filter { (season, episode) ->
            (season == 1 && season in bySeason || season in missing) && (season to episode) !in found
        }
        if (unmatched.isNotEmpty()) {
            val list = seasonList()
            val longest = list.filter { it.seasonNumber >= 1 }.maxOfOrNull { it.episodeCount } ?: 0
            for ((season, episode) in unmatched) {
                val (tmdbSeason, index) = TmdbEpisodeMapping.absoluteEpisode(list, episode) ?: continue
                val candidate = episodesOf(tmdbSeason)?.sortedBy { it.episodeNumber }?.getOrNull(index) ?: continue
                val fits = if (season == 1) candidate.episodeNumber == episode else episode > longest
                if (fits) found[season to episode] = candidate
            }
        }
        return Match(found, arc)
    }
}
