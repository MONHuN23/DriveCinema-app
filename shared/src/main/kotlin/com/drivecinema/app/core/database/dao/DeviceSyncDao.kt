package com.drivecinema.app.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind

/** A copy as Milestone Q sees it: which title it belongs to and where it lives. */
data class SyncSourceRow(
    val sourceKey: String,
    val mediaItemId: String,
    val kind: SourceKind,
    val isAvailable: Boolean
)

/** A catalog title's identity across devices: its TMDB ID, or its series' TMDB ID with the episode number. */
data class SyncTitleRow(
    val id: String,
    val type: MediaType,
    val tmdbId: Int?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val seriesTmdbId: Int?
)

/**
 * Milestone Q (device sync): reads the whole progress table and the catalog's identities in a few queries, and writes
 * merged rows. No schema change; it never deletes a row.
 */
@Dao
interface DeviceSyncDao {
    @Query("SELECT * FROM playback_progress")
    suspend fun allProgress(): List<PlaybackProgressEntity>

    @Query("SELECT sourceKey, mediaItemId, kind, isAvailable FROM media_sources")
    suspend fun allSources(): List<SyncSourceRow>

    @Query("""
        SELECT m.id AS id, m.type AS type, m.tmdbId AS tmdbId, m.seasonNumber AS seasonNumber,
               m.episodeNumber AS episodeNumber, s.tmdbId AS seriesTmdbId
        FROM media_items m LEFT JOIN series s ON s.id = m.seriesId
    """)
    suspend fun allTitles(): List<SyncTitleRow>

    /**
     * Writes a merged row only when it wins over the stored one, in one statement, so a save by the player during a
     * sync is never overwritten with older data. The condition is `ProgressMerge.wins`: a newer time, or the same time
     * and watched over unwatched, then a larger position.
     */
    @Query("""
        INSERT OR REPLACE INTO playback_progress (mediaItemId, positionMs, durationMs, isCompleted, lastWatchedAt)
        SELECT :key, :positionMs, :durationMs, :isCompleted, :lastWatchedAt
        WHERE NOT EXISTS (
            SELECT 1 FROM playback_progress WHERE mediaItemId = :key AND (
                lastWatchedAt > :lastWatchedAt
                OR (lastWatchedAt = :lastWatchedAt AND (
                    isCompleted > :isCompleted
                    OR (isCompleted = :isCompleted AND positionMs >= :positionMs)
                ))
            )
        )
    """)
    suspend fun saveIfNewer(key: String, positionMs: Long, durationMs: Long, isCompleted: Boolean, lastWatchedAt: Long)
}
