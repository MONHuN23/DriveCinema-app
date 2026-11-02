package com.drivecinema.app.core.metadata

/**
 * The folder and file names the naming helper suggests (Task R.1, design `docs/design/r0-naming-helper.md` §4).
 * These only build names; whether the app reads a name back is checked with the app's own parser and matcher
 * ([NamingGuide], [FolderCheck]), never by rules repeated here.
 */
object LibraryNames {

    const val SERIES_FOLDER = "Series"
    const val FILMS_FOLDER = "Films"

    /** Characters Windows or Drive refuse, or that read badly in a name; dropped. Control characters too. */
    private val DROPPED = Regex("""[:?*"<>|\p{Cntrl}]""")
    private val SLASHES = Regex("""[/\\]""")
    private val SPACES = Regex("""\s+""")

    /** [name] made safe for Drive and Windows: "Bleach: Thousand-Year Blood War" -> "Bleach Thousand-Year Blood War". */
    fun clean(name: String): String =
        name.replace(DROPPED, "").replace(SLASHES, " ").replace(SPACES, " ").trim().trimEnd('.', ' ')

    /** "Title (Year)", or "Title (Anime) (Year)": the tag goes before the year, which a year after it would lose. */
    fun seriesFolder(title: String, year: Int?, animeTag: Boolean): String =
        clean(title) + (if (animeTag) " (Anime)" else "") + (year?.let { " ($it)" } ?: "")

    /** An arc's own folder title: the show and the arc ("Bleach Thousand-Year Blood War"), the show once. */
    fun arcTitle(showTitle: String, arcName: String): String {
        val show = clean(showTitle)
        val arc = clean(arcName)
        return if (TmdbSeriesMatcher.normalize(arc).startsWith(TmdbSeriesMatcher.normalize(show))) arc else "$show $arc"
    }

    /** "Season 01"; specials are "Season 00" ("Specials" has no number and would be read as season 1). */
    fun seasonFolder(season: Int): String = "Season ${season.toString().padStart(2, '0')}"

    /** "Breaking Bad S01E01.mkv"; the episode has at least two digits ("One Piece S22E1086.mkv"). */
    fun episodeFile(seriesTitle: String, season: Int, episode: Int, extension: String): String =
        "${clean(seriesTitle)} S${season.toString().padStart(2, '0')}E${episode.toString().padStart(2, '0')}" +
            ext(extension)

    /** The fansub style: "One Piece - 1086.mkv", a running number. */
    fun fansubFile(seriesTitle: String, runningNumber: Int, extension: String): String =
        "${clean(seriesTitle)} - $runningNumber" + ext(extension)

    /** "Ted (2012).mkv", for the Films folder. */
    fun filmFile(title: String, year: Int?, extension: String): String =
        clean(title) + (year?.let { " ($it)" } ?: "") + ext(extension)

    /** The extension of [fileName] without the dot ("mkv"), or null. */
    fun extensionOf(fileName: String): String? =
        fileName.substringAfterLast('.', "").takeIf { it.isNotEmpty() && it.length <= 5 && '.' in fileName }

    /**
     * Whether a folder for [chosen] needs "(Anime)" (user, 2026-11-01): it's animated and TMDB's results list a
     * non-animated show with the same name ("One Piece", 1999 anime and 2023 live action).
     */
    fun needsAnimeTag(chosen: TmdbSeriesResult, results: List<TmdbSeriesResult>): Boolean {
        if (!chosen.animated) return false
        fun namesOf(r: TmdbSeriesResult) =
            setOf(r.metadata.title, r.originalName).map { TmdbSeriesMatcher.normalize(it) }.filter { it.isNotEmpty() }
        val names = namesOf(chosen)
        return results.any { other ->
            !other.animated && other.metadata.tmdbId != chosen.metadata.tmdbId && namesOf(other).any { it in names }
        }
    }

    private fun ext(extension: String) = "." + extension.trimStart('.')
}
