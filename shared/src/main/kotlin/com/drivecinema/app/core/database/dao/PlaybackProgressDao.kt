package com.drivecinema.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: PlaybackProgressEntity)

    @Query("SELECT * FROM playback_progress WHERE mediaItemId = :mediaItemId LIMIT 1")
    suspend fun getProgress(mediaItemId: String): PlaybackProgressEntity?

    @Query("SELECT * FROM playback_progress WHERE mediaItemId = :mediaItemId LIMIT 1")
    fun getProgressFlow(mediaItemId: String): Flow<PlaybackProgressEntity?>

    @Query("DELETE FROM playback_progress WHERE mediaItemId = :mediaItemId")
    suspend fun clearProgress(mediaItemId: String)
}
