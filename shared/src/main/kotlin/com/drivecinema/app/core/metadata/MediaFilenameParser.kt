package com.drivecinema.app.core.metadata

data class MovieParseResult(
    val cleanTitle: String,
    val year: Int?
)

data class EpisodeParseResult(
    val cleanShowName: String,
    val season: Int,
    val episode: Int,
    val episodeTitle: String? = null
)

object MediaFilenameParser {

    private val MOVIE_YEAR_REGEX = Regex(
        """^(?<title>.+?)[. _\-(\[]+(?<year>(?:19|20)\d{2})[)\]]?(?:[. _\-].*)?$""",
        RegexOption.IGNORE_CASE
    )

    private val EPISODE_REGEX = Regex(
        """^(?:(?<show>.+?)[. _\-]+)?[sS](?<season>\d{1,2})[eExX](?<episode>\d{1,2})(?:[. _\-]+(?<title>.*?))?[.]*$""",
        RegexOption.IGNORE_CASE
    )

    private val BARE_EPISODE_REGEX = Regex(
        """^(?:Episode\s*)?(?<episode>\d{1,3})(?:[. _\-]+(?<title>.*?))?[.]*$""",
        RegexOption.IGNORE_CASE
    )

    fun parseMovie(filename: String): MovieParseResult {
        val baseName = filename.substringBeforeLast('.')
        val match = MOVIE_YEAR_REGEX.find(baseName)
        if (match != null) {
            val rawTitle = match.groups["title"]?.value ?: baseName
            val yearStr = match.groups["year"]?.value
            val clean = sanitizeTitle(rawTitle)
            val year = yearStr?.toIntOrNull()
            return MovieParseResult(clean, year)
        }
        // No year: drop scene release tags ("Some.Film.1080p.WEB-DL" -> "Some Film").
        val withoutTags = stripReleaseTags(baseName)?.let { sanitizeTitle(it) }?.ifEmpty { null }
        return MovieParseResult(withoutTags ?: sanitizeTitle(baseName), null)
    }

    /**
     * Non-null only for names that clearly are episodes: a show name followed by SxxEyy
     * ("Rick.and.Morty.S01E04.720p.mkv"). Used to keep such files out of the films.
     */
    fun parseMarkedEpisode(filename: String): EpisodeParseResult? {
        val match = EPISODE_REGEX.find(filename.substringBeforeLast('.')) ?: return null
        if (match.groups["show"]?.value.isNullOrBlank()) return null
        return parseEpisode(filename).takeIf { it.cleanShowName.isNotEmpty() }
    }

    /**
     * "Season n" / "Sn" in a season folder name, as a word of its own ("Season 02", "S3", "Show_S4", "Show.S06.1080p");
     * 1 when there is none. Report F-14: an `s` at the end of another word no longer counts, so "Extras 2" or
     * "Bonus 2019" stay season 1 instead of 2 / 2019. Used by the Drive and the local-folder crawler alike.
     */
    fun parseSeasonFolder(folderName: String): Int =
        SEASON_FOLDER_REGEX.find(folderName)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    private val SEASON_FOLDER_REGEX = Regex("""(?<![a-z])(?:season|s)\s*(\d+)(?!\d)""", RegexOption.IGNORE_CASE)

    fun parseEpisode(filename: String): EpisodeParseResult {
        val baseName = filename.substringBeforeLast('.')
        val match = EPISODE_REGEX.find(baseName)
        if (match != null) {
            val rawShow = match.groups["show"]?.value ?: ""
            val season = match.groups["season"]?.value?.toIntOrNull() ?: 1
            val episode = match.groups["episode"]?.value?.toIntOrNull() ?: 1
            val rawTitle = match.groups["title"]?.value?.trim()?.ifEmpty { null }
            val cleanTitle = rawTitle?.let { stripReleaseTags(it) }?.let { sanitizeTitle(it) }?.ifEmpty { null }
            return EpisodeParseResult(sanitizeTitle(rawShow), season, episode, cleanTitle)
        }

        val bareMatch = BARE_EPISODE_REGEX.find(baseName)
        if (bareMatch != null) {
            val epNum = bareMatch.groups["episode"]?.value?.toIntOrNull() ?: 1
            val rawTitle = bareMatch.groups["title"]?.value?.trim()?.ifEmpty { null }
            val cleanTitle = rawTitle?.let { stripReleaseTags(it) }?.let { sanitizeTitle(it) }?.ifEmpty { null }
            return EpisodeParseResult("", 1, epNum, cleanTitle)
        }

        return EpisodeParseResult(sanitizeTitle(baseName), 1, 1, null)
    }

    /** Release tags that never belong to a title: resolution, codec, real source names, audio. */
    private val STRONG_RELEASE_TAG = Regex(
        "^(?:\\d{3,4}[pi]|x26[45]|h26[45]|hevc|av1|xvid|divx|10bit|8bit|webrip|webdl|bluray|bdrip|brrip|dvdrip|" +
            "hdtv|pdtv|remux|hdrip|aac\\d*|ac3|eac3|ddp?\\d*|dts\\w*|truehd|atmos)$",
        RegexOption.IGNORE_CASE
    )

    /** Words that are tags only at the end of what is left ("Movie WEB DL" but not "Charlotte's Web Story"). */
    private val WEAK_RELEASE_TAG = Regex(
        "^(?:web|dl|rip|dvd|uhd|4k|8k|hdr\\d*|dv|dovi|sdr|amzn|nf|dsnp|hmax|atvp|repack|proper|multi|subbed|dubbed|" +
            "flac|mp3|opus|avc)$",
        RegexOption.IGNORE_CASE
    )

    /**
     * Cuts a scene-style name at its first release tag ("Pilot.720p.WEB-DL.x264-GRP" -> "Pilot",
     * "720p-VARYG" -> null). Returns null when nothing but tags is left; names without a strong tag are kept.
     */
    fun stripReleaseTags(raw: String): String? {
        val tokens = raw.split('.', ' ', '_', '-').filter { it.isNotEmpty() }
        val firstStrong = tokens.indexOfFirst { STRONG_RELEASE_TAG.matches(it) }
        if (firstStrong < 0) return raw.trim().ifEmpty { null }
        val kept = tokens.take(firstStrong).dropLastWhile { WEAK_RELEASE_TAG.matches(it) }
        return kept.joinToString(" ").trim().ifEmpty { null }
    }

    fun sanitizeTitle(raw: String): String {
        return raw
            .replace('.', ' ')
            .replace('_', ' ')
            .replace('-', ' ')
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('(', ' ')
            .replace(')', ' ')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
}
