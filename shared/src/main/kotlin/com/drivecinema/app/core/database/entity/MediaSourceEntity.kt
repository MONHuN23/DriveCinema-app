package com.drivecinema.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceKind {
    DRIVE,
    LOCAL,
    /** Task I.2: a file on a paired DriveCinema PC (`dchost://` key), streamed over the home network or a VPN. */
    HOST
}

/**
 * One playable copy of a catalog item (`media_items.id`). Watch progress stays keyed by [sourceKey]
 * (Drive file ID or SAF content URI) and is aggregated per item on read, so merging or un-merging
 * sources never rewrites `playback_progress`.
 */
@Entity(
    tableName = "media_sources",
    indices = [
        Index("mediaItemId"),
        Index("libraryRootId")
    ]
)
data class MediaSourceEntity(
    @PrimaryKey val sourceKey: String,
    val mediaItemId: String,
    val kind: SourceKind,
    val libraryRootId: String? = null,
    val sizeBytes: Long? = null,
    /** false = not found on the last successful scan or permission lost; the row is kept. */
    val isAvailable: Boolean = true,
    val lastSeenAt: Long = System.currentTimeMillis(),
    // Task L.3 (schema v5): what is known about this copy. Null = unknown, never guessed. Size, resolution and
    // length come from Drive at sync time (Drive copies) or from the player; codecs only from the player.
    val widthPx: Int? = null,
    val heightPx: Int? = null,
    val durationMs: Long? = null,
    /** "MKV", from the file name. */
    val container: String? = null,
    /** Display names from `MediaInfoFormat`: "HEVC", "E-AC-3". */
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val audioChannels: Int? = null,
    val frameRate: Float? = null,
    val isHdr: Boolean? = null
)

/** A source that may duplicate existing catalog items; waits for the user to confirm or reject the merge. */
@Entity(tableName = "pending_matches")
data class PendingMatchEntity(
    @PrimaryKey val sourceKey: String,
    /** Comma-separated `media_items.id` candidates. */
    val candidateItemIds: String,
    val createdAt: Long = System.currentTimeMillis()
)
