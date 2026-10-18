package com.drivecinema.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MediaType {
    MOVIE,
    EPISODE
}

@Entity(
    tableName = "media_items",
    indices = [
        Index("parentFolderId"),
        Index("type"),
        Index("title"),
        Index("isDeleted")
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val type: MediaType,
    val title: String,
    val year: Int? = null,
    val runtimeSeconds: Int? = null,
    val driveFileId: String = id,
    val parentFolderId: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val seriesId: String? = null,
    val tmdbId: Int? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val overview: String? = null,
    val genres: String? = null,
    val voteAverage: Double? = null,
    val voteCount: Int? = null,
    val isDeleted: Boolean = false,
    val lastSyncedAt: Long = System.currentTimeMillis()
)
