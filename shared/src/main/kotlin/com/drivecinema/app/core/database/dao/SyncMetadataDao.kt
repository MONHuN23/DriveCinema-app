package com.drivecinema.app.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.drivecinema.app.core.database.entity.SyncMetadataEntity

@Dao
interface SyncMetadataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setMetadata(metadata: SyncMetadataEntity)

    @Query("SELECT * FROM sync_metadata WHERE key = :key LIMIT 1")
    suspend fun getMetadata(key: String): SyncMetadataEntity?

    @Query("DELETE FROM sync_metadata WHERE key = :key")
    suspend fun clearMetadata(key: String)

    /** Keys starting with [prefix] (plain comparison, so `_` and `%` in it are not wildcards). */
    @Query("SELECT key FROM sync_metadata WHERE substr(key, 1, length(:prefix)) = :prefix")
    suspend fun getKeysWithPrefix(prefix: String): List<String>

    /** Removes the rows under [prefix] whose timestamp is at or before [timestamp]. */
    @Query("DELETE FROM sync_metadata WHERE substr(key, 1, length(:prefix)) = :prefix AND lastSyncTimestamp <= :timestamp")
    suspend fun deleteWithPrefixUpTo(prefix: String, timestamp: Long)
}
