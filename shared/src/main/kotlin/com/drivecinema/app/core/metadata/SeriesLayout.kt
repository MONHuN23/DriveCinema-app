package com.drivecinema.app.core.metadata

/** Where an episode file sits in a series: (season, episode), and how its name was read. */
data class EpisodeKey(
    val season: Int,
    val episode: Int,
    val pattern: EpisodeNamePattern,
    /** The season in the file's own name ("S02E03"), when it has one; may differ from a season folder's. */
    val fileSeason: Int? = null,
    /** The season folder had a name but no number in it ("Specials", "Extras"); it counts as season 1. */
    val seasonFolderWithoutNumber: Boolean = false,
    /** The episode title in the file name, release tags removed ("Show S01E01 Pilot" -> "Pilot"). */
    val episodeTitle: String? = null
) {
    val pair: Pair<Int, Int> get() = season to episode
}

/**
 * The `Series/<show>/<season>/<file>` layout's rule for an episode's season and number, used by the Drive crawl
 * ([com.drivecinema.app.core.sync.FolderQueueCrawler]), the local-folder crawl and the naming helper (Task R.1), so
 * there is one copy of it. In a season folder the folder gives the season ("Season 02", report F-14) and the file its
 * number; a file directly in the show folder gives both ("Show S02E03"), season 1 otherwise.
 */
object SeriesLayout {

    fun episodeKey(seasonFolderName: String?, fileName: String): EpisodeKey {
        val parsed = MediaFilenameParser.parseEpisode(fileName)
        val episode = if (parsed.episode > 0) parsed.episode else (episodeNumberFallback(fileName) ?: 1)
        val fileSeason = parsed.season.takeIf { parsed.pattern == EpisodeNamePattern.SEASON_EPISODE }
        if (seasonFolderName != null) {
            val folderSeason = MediaFilenameParser.parseSeasonFolderOrNull(seasonFolderName)
            return EpisodeKey(
                season = folderSeason ?: 1,
                episode = episode,
                pattern = parsed.pattern,
                fileSeason = fileSeason,
                seasonFolderWithoutNumber = folderSeason == null,
                episodeTitle = parsed.episodeTitle
            )
        }
        val season = if (parsed.cleanShowName.isNotEmpty() && parsed.season > 0) parsed.season else 1
        return EpisodeKey(season, episode, parsed.pattern, fileSeason, episodeTitle = parsed.episodeTitle)
    }

    /** An episode number of 0 from the parser: "S01E00"-like names keep the old crawler's second look. */
    private fun episodeNumberFallback(fileName: String): Int? {
        val match = Regex("""[se]\d+[e|x](\d+)""", RegexOption.IGNORE_CASE).find(fileName)
            ?: Regex("""episode\s*(\d+)""", RegexOption.IGNORE_CASE).find(fileName)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }
}
