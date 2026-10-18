package com.drivecinema.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PendingMatchEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import kotlinx.coroutines.flow.Flow

/** A watched catalog title with its latest progress across all copies (input for Continue Watching). */
data class WatchHistoryRow(
    val id: String,
    val title: String,
    val type: MediaType,
    val posterPath: String?,
    val backdropPath: String?,
    val seriesId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean,
    val lastWatchedAt: Long
)

/** Read side of the unified catalog: an item's sources and its progress aggregated across them. */
@Dao
interface MediaSourceDao {
    @Query("SELECT * FROM media_sources WHERE mediaItemId = :mediaItemId")
    suspend fun getSourcesForItem(mediaItemId: String): List<MediaSourceEntity>

    @Query("SELECT * FROM media_sources WHERE sourceKey = :sourceKey LIMIT 1")
    suspend fun getSource(sourceKey: String): MediaSourceEntity?

    /**
     * Most recently watched progress for a catalog item across all of its sources (Drive file ID,
     * local content URIs), plus any legacy row keyed directly by the item ID.
     */
    @Query("""
        SELECT * FROM playback_progress
        WHERE mediaItemId = :mediaItemId
           OR mediaItemId IN (SELECT sourceKey FROM media_sources WHERE mediaItemId = :mediaItemId)
        ORDER BY lastWatchedAt DESC
        LIMIT 1
    """)
    fun getLatestProgressForItemFlow(mediaItemId: String): Flow<PlaybackProgressEntity?>

    @Query("""
        SELECT * FROM playback_progress
        WHERE mediaItemId = :mediaItemId
           OR mediaItemId IN (SELECT sourceKey FROM media_sources WHERE mediaItemId = :mediaItemId)
        ORDER BY lastWatchedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestProgressForItem(mediaItemId: String): PlaybackProgressEntity?

    /**
     * Latest progress of every episode of a series, one row per catalog episode (across its Drive and local
     * copies), with `mediaItemId` set to the catalog ID. Same aggregation as Continue Watching.
     */
    @Query("""
        SELECT m.id AS mediaItemId, p.positionMs AS positionMs, p.durationMs AS durationMs,
               p.isCompleted AS isCompleted, MAX(p.lastWatchedAt) AS lastWatchedAt
        FROM playback_progress p
        INNER JOIN media_items m ON m.id = COALESCE(
            (SELECT s.mediaItemId FROM media_sources s WHERE s.sourceKey = p.mediaItemId),
            p.mediaItemId
        )
        WHERE m.seriesId = :seriesId
        GROUP BY m.id
    """)
    fun getSeriesProgressFlow(seriesId: String): Flow<List<PlaybackProgressEntity>>

    /**
     * Latest progress of every watched catalog title, finished ones included, newest first: the input for
     * Continue Watching (Milestone J.2). Same aggregation across copies as [getSeriesProgressFlow].
     */
    @Query("""
        SELECT id, title, type, posterPath, backdropPath, seriesId, seasonNumber, episodeNumber,
               positionMs, durationMs, isCompleted, lastWatchedAt FROM (
            SELECT m.id AS id, m.title AS title, m.type AS type, m.posterPath AS posterPath,
                   m.backdropPath AS backdropPath, m.seriesId AS seriesId, m.seasonNumber AS seasonNumber,
                   m.episodeNumber AS episodeNumber, p.positionMs AS positionMs, p.durationMs AS durationMs,
                   p.isCompleted AS isCompleted, MAX(p.lastWatchedAt) AS lastWatchedAt
            FROM playback_progress p
            INNER JOIN media_items m ON m.id = COALESCE(
                (SELECT s.mediaItemId FROM media_sources s WHERE s.sourceKey = p.mediaItemId),
                p.mediaItemId
            )
            WHERE m.isDeleted = 0
            GROUP BY m.id
        )
        ORDER BY lastWatchedAt DESC
        LIMIT 500
    """)
    fun getWatchHistoryFlow(): Flow<List<WatchHistoryRow>>

    // ---- Local library indexing (Task C.4 phase 2) ----

    @Query("SELECT * FROM media_items WHERE type = 'MOVIE'")
    suspend fun getAllMovieItems(): List<MediaItemEntity>

    @Query("SELECT * FROM series")
    suspend fun getAllSeriesRows(): List<SeriesEntity>

    @Query("SELECT * FROM media_items WHERE type = 'EPISODE' AND seriesId = :seriesId")
    suspend fun getAllEpisodesOfSeries(seriesId: String): List<MediaItemEntity>

    @Query("SELECT sourceKey FROM media_sources WHERE sourceKey IN (:sourceKeys)")
    suspend fun getExistingSourceKeys(sourceKeys: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItemsIfAbsent(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSeriesIfAbsent(series: List<SeriesEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSourcesIfAbsent(sources: List<MediaSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPendingMatchesIfAbsent(matches: List<PendingMatchEntity>)

    /** Re-titles a catalog entry the app itself created from a file name (only called for local IDs). */
    @Query("UPDATE media_items SET title = :title WHERE id = :id AND title != :title")
    suspend fun updateTitle(id: String, title: String)

    @Query("UPDATE media_sources SET isAvailable = 0 WHERE kind = 'LOCAL' AND libraryRootId = :treeUri")
    suspend fun markLocalTreeUnavailable(treeUri: String)

    /** Local folders the user removed (or whose permission was revoked) stop being playable; rows are kept. */
    @Query("UPDATE media_sources SET isAvailable = 0 WHERE kind = 'LOCAL' AND (libraryRootId IS NULL OR libraryRootId NOT IN (:savedTreeUris))")
    suspend fun markLocalSourcesOutsideUnavailable(savedTreeUris: List<String>)

    @Query("UPDATE media_sources SET isAvailable = 1, lastSeenAt = :seenAt, libraryRootId = :libraryRootId WHERE sourceKey IN (:sourceKeys)")
    suspend fun markSourcesAvailable(sourceKeys: List<String>, libraryRootId: String?, seenAt: Long)

    /** An item is visible while at least one of its sources (Drive or local) is available. */
    @Query("""
        UPDATE media_items SET isDeleted = CASE WHEN EXISTS (
            SELECT 1 FROM media_sources s WHERE s.mediaItemId = media_items.id AND s.isAvailable = 1
        ) THEN 0 ELSE 1 END
    """)
    suspend fun refreshVisibilityFromSources()

    // ---- Possible duplicates ----

    @Query("SELECT * FROM pending_matches ORDER BY createdAt ASC")
    fun getPendingMatchesFlow(): Flow<List<PendingMatchEntity>>

    @Query("SELECT * FROM pending_matches ORDER BY createdAt ASC")
    suspend fun getPendingMatches(): List<PendingMatchEntity>

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    suspend fun getItemsByIds(ids: List<String>): List<MediaItemEntity>

    @Query("UPDATE media_sources SET mediaItemId = :mediaItemId WHERE sourceKey = :sourceKey")
    suspend fun relinkSource(sourceKey: String, mediaItemId: String)

    @Query("DELETE FROM pending_matches WHERE sourceKey = :sourceKey")
    suspend fun deletePendingMatch(sourceKey: String)

    /** The user confirmed the local copy is the same title: link it and hide the separate entry. */
    @Transaction
    suspend fun confirmSameTitle(sourceKey: String, mediaItemId: String) {
        relinkSource(sourceKey, mediaItemId)
        deletePendingMatch(sourceKey)
        refreshVisibilityFromSources()
    }
}
