package com.drivecinema.app.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "playback_progress",
    indices = [
        Index("lastWatchedAt")
    ]
)
data class PlaybackProgressEntity(
    @PrimaryKey val mediaItemId: String,
    val positionMs: Long,
    val durationMs: Long,
    val isCompleted: Boolean = false,
    val lastWatchedAt: Long = System.currentTimeMillis()
)
