package com.drivecinema.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.drivecinema.app.core.database.dao.MediaDao
import com.drivecinema.app.core.database.dao.MediaSourceDao
import com.drivecinema.app.core.database.dao.PlaybackProgressDao
import com.drivecinema.app.core.database.dao.SyncMetadataDao
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.PendingMatchEntity
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SeriesEntity
import com.drivecinema.app.core.database.entity.SyncMetadataEntity

@Database(
    entities = [
        MediaItemEntity::class,
        SeriesEntity::class,
        PlaybackProgressEntity::class,
        SyncMetadataEntity::class,
        MediaSourceEntity::class,
        PendingMatchEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class DriveCinemaDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun mediaSourceDao(): MediaSourceDao

    /**
     * Platform builders are extension functions on this companion: Android (`getInstance(context)`, with
     * the Android-only migrations) lives in `:app`, desktop builds a fresh database with the bundled SQLite driver.
     */
    companion object {
        const val DATABASE_NAME = "drivecinema.db"
    }
}
