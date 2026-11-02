package com.drivecinema.app.core.metadata

/**
 * The TMDB show a series folder finds, the way the sync searches it (report F-43): "One Piece (Anime)" (read as
 * "One Piece Anime") searches "One Piece" among animated shows, then the full title if that finds nothing. The sync
 * and the naming helper (Task R.1) both use this, so a suggested folder name is checked with the sync's own search.
 */
object SeriesFolderSearch {

    /** [show] as TMDB has it; [searchTitle] is the title that found it, which the episode matcher compares. */
    data class Found(val show: TmdbMetadata, val searchTitle: String)

    /** Success with null: TMDB has no such show. Failure: a request failed (nothing is known). */
    suspend fun find(tmdb: TmdbClient, folderTitle: String, year: Int?): Result<Found?> {
        val untagged = TmdbSeriesMatcher.withoutAnimeTag(folderTitle)
        var searchTitle = untagged ?: folderTitle
        var result = tmdb.searchSeries(searchTitle, year, animated = untagged != null)
        if (untagged != null && result.isSuccess && result.getOrNull() == null) {
            searchTitle = folderTitle
            result = tmdb.searchSeries(searchTitle, year)
        }
        return result.map { show -> show?.let { Found(it, searchTitle) } }
    }

    /** [find] for a series folder's name ("One Piece (Anime) (1999)"), read as the crawler reads it. */
    suspend fun findFolder(tmdb: TmdbClient, folderName: String): Result<Found?> {
        val parsed = MediaFilenameParser.parseMovie(folderName)
        return find(tmdb, parsed.cleanTitle, parsed.year)
    }
}
