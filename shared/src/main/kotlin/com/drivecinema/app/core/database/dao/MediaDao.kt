package com.drivecinema.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.drivecinema.app.core.database.CrawledCatalogMerge
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.SourceKind
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.sync.DriveFileInfo
import kotlinx.coroutines.flow.Flow

data class ContinueWatchingItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val posterPath: String?,
    val backdropPath: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long,
    /** Set for episodes; [title] is then the series title (Milestone J.2). */
    val seriesId: String? = null,
    /** "S1E4" for episodes. */
    val episodeCode: String? = null,
    /** The series' last watched episode is finished; this card is the following episode, from the start. */
    val isUpNext: Boolean = false
)

/** Result row of [MediaDao.getEpisodeCountsFlow]. */
data class SeriesEpisodeCount(val seriesId: String, val episodeCount: Int)

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItems(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMediaItem(item: MediaItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSingleSeries(series: SeriesEntity)

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun getSeriesById(id: String): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    fun getSeriesByIdFlow(id: String): Flow<SeriesEntity?>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getMediaItemById(id: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getMediaItemsByIds(ids: List<String>): List<MediaItemEntity>

    @Query("SELECT * FROM series WHERE id IN (:ids)")
    suspend fun getSeriesByIds(ids: List<String>): List<SeriesEntity>

    @Query("SELECT * FROM media_items WHERE type = 'MOVIE' AND isDeleted = 0 ORDER BY title ASC")
    fun getAllMoviesFlow(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE type = 'MOVIE' AND isDeleted = 0 ORDER BY title ASC")
    suspend fun getAllMovies(): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE type = 'MOVIE' AND tmdbId IS NULL AND isDeleted = 0")
    suspend fun getUnenrichedMovies(): List<MediaItemEntity>

    // A series is listed only while it has a playable episode. Found on the device: a show folder deleted or merged
    // on Drive left an empty second show in the library (its row stays, with no episodes left).
    @Query("""
        SELECT * FROM series
        WHERE EXISTS (SELECT 1 FROM media_items m WHERE m.seriesId = series.id AND m.isDeleted = 0)
        ORDER BY title ASC
    """)
    fun getAllSeriesFlow(): Flow<List<SeriesEntity>>

    /** Task L.3: what Drive (or a local scan) reports about a copy; a null leaves the stored value. */
    @Query("""
        UPDATE media_sources SET
            sizeBytes = COALESCE(:sizeBytes, sizeBytes), widthPx = COALESCE(:widthPx, widthPx),
            heightPx = COALESCE(:heightPx, heightPx), durationMs = COALESCE(:durationMs, durationMs),
            container = COALESCE(:container, container)
        WHERE sourceKey = :sourceKey
    """)
    suspend fun updateFileInfo(sourceKey: String, sizeBytes: Long?, widthPx: Int?, heightPx: Int?, durationMs: Long?, container: String?)

    /** Playable episodes per series, for "3 of 10 watched" on the desktop's Series view (Task L.1). */
    @Query("""
        SELECT seriesId, COUNT(*) AS episodeCount FROM media_items
        WHERE type = 'EPISODE' AND isDeleted = 0 AND seriesId IS NOT NULL
        GROUP BY seriesId
    """)
    fun getEpisodeCountsFlow(): Flow<List<SeriesEpisodeCount>>

    @Query("SELECT * FROM series ORDER BY title ASC")
    suspend fun getAllSeries(): List<SeriesEntity>

    // Task N.2 (F-40): only series the library shows (with a playable episode), like getAllSeriesFlow.
    @Query("""
        SELECT * FROM series
        WHERE tmdbId IS NULL
          AND EXISTS (SELECT 1 FROM media_items m WHERE m.seriesId = series.id AND m.isDeleted = 0)
    """)
    suspend fun getUnenrichedSeries(): List<SeriesEntity>

    @Query("SELECT * FROM media_items WHERE type = 'MOVIE' AND isDeleted = 0 AND (:query = '' OR title LIKE '%' || :query || '%') ORDER BY title ASC")
    fun searchMoviesFlow(query: String): Flow<List<MediaItemEntity>>

    @Query("""
        SELECT * FROM series
        WHERE (:query = '' OR title LIKE '%' || :query || '%')
          AND EXISTS (SELECT 1 FROM media_items m WHERE m.seriesId = series.id AND m.isDeleted = 0)
        ORDER BY title ASC
    """)
    fun searchSeriesFlow(query: String): Flow<List<SeriesEntity>>

    @Query("SELECT * FROM media_items WHERE seriesId = :seriesId AND isDeleted = 0 ORDER BY seasonNumber ASC, episodeNumber ASC")
    fun getEpisodesForSeriesFlow(seriesId: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE seriesId = :seriesId AND isDeleted = 0 ORDER BY seasonNumber ASC, episodeNumber ASC")
    suspend fun getEpisodesForSeries(seriesId: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE parentFolderId = :folderId AND isDeleted = 0")
    suspend fun getItemsInFolder(folderId: String): List<MediaItemEntity>

    @Query("UPDATE media_items SET isDeleted = 1 WHERE id = :id")
    suspend fun markDeleted(id: String)

    @Query("UPDATE media_items SET isDeleted = 1 WHERE parentFolderId = :folderId")
    suspend fun markFolderItemsDeleted(folderId: String)

    @Query("UPDATE media_sources SET isAvailable = 0 WHERE kind = :kind")
    suspend fun markSourcesUnavailable(kind: SourceKind)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourcesIfAbsent(sources: List<MediaSourceEntity>)

    @Query("UPDATE media_sources SET isAvailable = 1, lastSeenAt = :seenAt, libraryRootId = :libraryRootId WHERE sourceKey IN (:sourceKeys)")
    suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long)

    @Query("SELECT * FROM media_sources WHERE sourceKey IN (:sourceKeys)")
    suspend fun getSourcesByKeys(sourceKeys: List<String>): List<MediaSourceEntity>

    /** An item is visible while at least one of its sources (Drive or local) is available. */
    @Query("""
        UPDATE media_items SET isDeleted = CASE WHEN EXISTS (
            SELECT 1 FROM media_sources s WHERE s.mediaItemId = media_items.id AND s.isAvailable = 1
        ) THEN 0 ELSE 1 END
    """)
    suspend fun refreshVisibilityFromSources()

    /**
     * Writes crawled series, keeping the TMDB enrichment of series whose folder name is unchanged.
     * Series in [rematchSeriesIds] keep their artwork but are re-matched so new episodes get enriched.
     */
    @Transaction
    suspend fun upsertCrawledSeries(series: List<SeriesEntity>, rematchSeriesIds: Set<String> = emptySet()) {
        val stored = series.map { it.id }.chunked(500).flatMap { getSeriesByIds(it) }.associateBy { it.id }
        // Task N.1: unchanged rows are not written again.
        series.map { CrawledCatalogMerge.mergeSeries(it, stored[it.id], rematch = it.id in rematchSeriesIds) }
            .filter { it != stored[it.id] }
            .takeIf { it.isNotEmpty() }
            ?.let { insertSeries(it) }
    }

    /**
     * Atomically makes the crawled Drive library the only available set of Drive sources. Drive sources
     * not in it are flagged unavailable (never removed) and items without any available source are
     * soft-deleted; local sources are untouched. Existing source → item links (merges) are preserved,
     * TMDB enrichment of unchanged items is kept (see [CrawledCatalogMerge]), and playback_progress is
     * never touched.
     *
     * Task N.1: only rows that change are written. M.0 measured a refresh of an unchanged library rewriting every
     * item, source and file-info row (about 850 statements, 0.6–0.9 s on the TV). An unchanged item keeps its
     * `lastSyncedAt` (nothing reads it); `media_sources.lastSeenAt` still records every crawl.
     */
    @Transaction
    suspend fun replaceVisibleLibrary(
        movies: List<MediaItemEntity>,
        series: List<SeriesEntity>,
        episodes: List<MediaItemEntity>,
        libraryRootId: String? = null,
        seenAt: Long = System.currentTimeMillis(),
        /** Task L.3: size / resolution / length per Drive file ID, from the same crawl. */
        fileInfo: Map<String, DriveFileInfo> = emptyMap(),
        /** F-48: series matching changed since the stored matches were made, so every series is matched again. */
        rematchAllSeries: Boolean = false
    ) {
        markSourcesUnavailable(SourceKind.DRIVE)
        val crawled = movies + episodes
        // Chunked to stay under SQLite's 999 bound-variable limit on older devices.
        val stored = crawled.map { it.id }.chunked(500).flatMap { getMediaItemsByIds(it) }.associateBy { it.id }
        val seriesWithNewEpisodes = episodes
            .filterNot { CrawledCatalogMerge.isUnchanged(it, stored[it.id]) }
            .mapNotNull { it.seriesId }
            .toSet()
        // Series matched again from scratch: their episodes start from their file names (F-48).
        val storedSeries = series.map { it.id }.chunked(500).flatMap { getSeriesByIds(it) }.associateBy { it.id }
        val freshSeries = if (rematchAllSeries) series.map { it.id }.toSet()
            else series.filter { CrawledCatalogMerge.isRenamed(it, storedSeries[it.id]) }.map { it.id }.toSet()
        fun changedRows(items: List<MediaItemEntity>) = items
            .map { CrawledCatalogMerge.mergeItem(it, stored[it.id], keepEnrichment = it.seriesId !in freshSeries) }
            .filter { merged -> stored[merged.id]?.copy(lastSyncedAt = merged.lastSyncedAt) != merged }
        changedRows(movies).takeIf { it.isNotEmpty() }?.let { insertMediaItems(it) }
        upsertCrawledSeries(series, rematchSeriesIds = if (rematchAllSeries) freshSeries else seriesWithNewEpisodes)
        changedRows(episodes).takeIf { it.isNotEmpty() }?.let { insertMediaItems(it) }
        val storedSources = crawled.map { it.id }.chunked(500).flatMap { getSourcesByKeys(it) }.associateBy { it.sourceKey }
        crawled.filterNot { it.id in storedSources }.takeIf { it.isNotEmpty() }?.let { newItems ->
            insertSourcesIfAbsent(
                newItems.map {
                    MediaSourceEntity(
                        sourceKey = it.id,
                        mediaItemId = it.id,
                        kind = SourceKind.DRIVE,
                        libraryRootId = libraryRootId,
                        lastSeenAt = seenAt
                    )
                }
            )
        }
        crawled.map { it.id }.chunked(500).forEach { keys ->
            markSourcesAvailable(keys, libraryRootId, seenAt)
        }
        fileInfo.forEach { (fileId, info) ->
            // updateFileInfo keeps a stored value where Drive says nothing (null), so only a new non-null value changes the row.
            val source = storedSources[fileId]
            val changes = source == null ||
                (info.sizeBytes != null && info.sizeBytes != source.sizeBytes) ||
                (info.widthPx != null && info.widthPx != source.widthPx) ||
                (info.heightPx != null && info.heightPx != source.heightPx) ||
                (info.durationMs != null && info.durationMs != source.durationMs) ||
                (info.container != null && info.container != source.container)
            if (changes) updateFileInfo(fileId, info.sizeBytes, info.widthPx, info.heightPx, info.durationMs, info.container)
        }
        refreshVisibilityFromSources()
    }

    /**
     * One row per catalog item, using the most recently watched progress across all of its sources
     * (progress is keyed by source; see media_sources). Items whose latest progress is completed are excluded.
     * Raw per-title list; the home screens show [com.drivecinema.app.core.player.ContinueWatching] instead,
     * which keeps one card per series (Milestone J.2).
     */
    @Query("""
        SELECT id, title, type, posterPath, backdropPath, positionMs, durationMs, lastWatchedAt,
               NULL AS seriesId, NULL AS episodeCode, 0 AS isUpNext FROM (
            SELECT m.id AS id, m.title AS title, m.type AS type, m.posterPath AS posterPath,
                   m.backdropPath AS backdropPath, p.positionMs AS positionMs, p.durationMs AS durationMs,
                   p.isCompleted AS isCompleted, MAX(p.lastWatchedAt) AS lastWatchedAt
            FROM playback_progress p
            INNER JOIN media_items m ON m.id = COALESCE(
                (SELECT s.mediaItemId FROM media_sources s WHERE s.sourceKey = p.mediaItemId),
                p.mediaItemId
            )
            WHERE m.isDeleted = 0
            GROUP BY m.id
        )
        WHERE isCompleted = 0
        ORDER BY lastWatchedAt DESC
        LIMIT 20
    """)
    fun getContinueWatchingFlow(): Flow<List<ContinueWatchingItem>>
}
