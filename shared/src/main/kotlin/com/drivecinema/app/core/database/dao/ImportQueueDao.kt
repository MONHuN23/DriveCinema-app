package com.drivecinema.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.drivecinema.app.core.database.entity.ImportQueueEntity
import com.drivecinema.app.core.database.entity.ImportState
import kotlinx.coroutines.flow.Flow

/** Desktop import queue (Task I.1). Never touches the catalog or `playback_progress`. */
@Dao
interface ImportQueueDao {
    /** Returns -1 when the file is already queued (same path, size and time): duplicate watch events change nothing. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: ImportQueueEntity): Long

    @Update
    suspend fun update(entry: ImportQueueEntity)

    @Query("SELECT * FROM import_queue WHERE importKey = :importKey LIMIT 1")
    suspend fun get(importKey: String): ImportQueueEntity?

    @Query("SELECT * FROM import_queue ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ImportQueueEntity>>

    @Query("SELECT * FROM import_queue WHERE state = :state ORDER BY createdAt ASC")
    suspend fun withState(state: ImportState): List<ImportQueueEntity>

    @Query("SELECT * FROM import_queue WHERE state = 'QUEUED' AND libraryRootId = :libraryRootId ORDER BY createdAt ASC LIMIT 1")
    suspend fun nextQueued(libraryRootId: String): ImportQueueEntity?

    /** After a crash or restart: uploads that were running go back to the queue (their session is kept). */
    @Query("UPDATE import_queue SET state = 'QUEUED' WHERE state = 'UPLOADING'")
    suspend fun requeueInterrupted(): Int

    @Query("UPDATE import_queue SET state = :state, message = :message, updatedAt = :now WHERE importKey = :importKey")
    suspend fun setState(importKey: String, state: ImportState, message: String?, now: Long)

    @Query("UPDATE import_queue SET sessionUri = :sessionUri, updatedAt = :now WHERE importKey = :importKey")
    suspend fun setSession(importKey: String, sessionUri: String?, now: Long)

    @Query("UPDATE import_queue SET bytesUploaded = :bytes WHERE importKey = :importKey")
    suspend fun setProgress(importKey: String, bytes: Long)

    /** "Clear finished": removes done and cancelled entries (only the list; files on Drive and on disk stay). */
    @Query("DELETE FROM import_queue WHERE state IN ('DONE', 'CANCELLED')")
    suspend fun clearFinished(): Int
}
