package com.drivecinema.app.core.database

import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity

/**
 * Merges freshly crawled catalog rows (which carry only filename-derived fields) into the rows already
 * stored, so a re-sync keeps TMDB enrichment for unchanged items instead of blanking posters until the
 * next enrichment pass. A row whose parsed identity changed (renamed or renumbered file) loses its
 * enrichment so it gets matched again.
 */
object CrawledCatalogMerge {

    /**
     * Movies and series keep their parsed title (enrichment never overwrites it); the year counts as changed
     * only when the new name has one that differs, since a stored year may have been filled in by TMDB.
     */
    private fun sameParsedIdentity(crawledTitle: String, crawledYear: Int?, storedTitle: String, storedYear: Int?) =
        crawledTitle == storedTitle && (crawledYear == null || crawledYear == storedYear)

    /** Episode titles come from TMDB once enriched, so episodes are identified by series, season and number. */
    private fun sameEpisodeIdentity(crawled: MediaItemEntity, stored: MediaItemEntity) =
        crawled.seriesId == stored.seriesId &&
            crawled.seasonNumber == stored.seasonNumber &&
            crawled.episodeNumber == stored.episodeNumber

    fun isUnchanged(crawled: MediaItemEntity, stored: MediaItemEntity?): Boolean {
        if (stored == null || stored.type != crawled.type) return false
        return when (crawled.type) {
            MediaType.MOVIE -> sameParsedIdentity(crawled.title, crawled.year, stored.title, stored.year)
            MediaType.EPISODE -> sameEpisodeIdentity(crawled, stored)
        }
    }

    /** Crawl-owned columns come from [crawled]; enrichment columns are kept from [stored] when unchanged. */
    fun mergeItem(crawled: MediaItemEntity, stored: MediaItemEntity?): MediaItemEntity {
        if (stored == null || !isUnchanged(crawled, stored)) return crawled
        return crawled.copy(
            title = stored.title,
            year = stored.year,
            runtimeSeconds = stored.runtimeSeconds,
            tmdbId = stored.tmdbId,
            posterPath = stored.posterPath,
            backdropPath = stored.backdropPath,
            overview = stored.overview,
            genres = stored.genres,
            voteAverage = stored.voteAverage,
            voteCount = stored.voteCount
        )
    }

    /**
     * @param rematch the series has new or renumbered episodes: its tmdbId is cleared so the enrichment
     *   pass fetches their episode titles, but its artwork is kept meanwhile.
     */
    fun mergeSeries(crawled: SeriesEntity, stored: SeriesEntity?, rematch: Boolean = false): SeriesEntity {
        if (stored == null || !sameParsedIdentity(crawled.title, crawled.year, stored.title, stored.year)) {
            return crawled
        }
        return crawled.copy(
            year = stored.year,
            tmdbId = if (rematch) null else stored.tmdbId,
            posterPath = stored.posterPath,
            backdropPath = stored.backdropPath,
            overview = stored.overview,
            genres = stored.genres,
            voteAverage = stored.voteAverage,
            voteCount = stored.voteCount
        )
    }
}
