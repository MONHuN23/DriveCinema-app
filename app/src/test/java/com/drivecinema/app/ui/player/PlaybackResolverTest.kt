package com.drivecinema.app.ui.player

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.DriveCinemaDatabase
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PlaybackResolverTest {

    private lateinit var db: DriveCinemaDatabase
    private lateinit var resolver: PlaybackResolver

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), DriveCinemaDatabase::class.java)
            .allowMainThreadQueries().build()
        resolver = PlaybackResolver(db.mediaSourceDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun item(id: String) =
        db.mediaDao().insertMediaItem(MediaItemEntity(id = id, type = MediaType.MOVIE, title = id, parentFolderId = "f"))

    private suspend fun source(key: String, item: String, kind: SourceKind, available: Boolean = true) =
        db.mediaDao().insertSourcesIfAbsent(listOf(MediaSourceEntity(key, item, kind, isAvailable = available)))

    @Test
    fun legacyDriveItemWithoutSourcesPlaysFromDrive() = runBlocking {
        item("drive1")
        assertEquals(ResolvedPlayback.Playable(PlaybackSource.Drive("drive1"), 0L), resolver.resolve("drive1"))
    }

    @Test
    fun localOnlyTitleWithoutAvailableCopyIsUnavailable() = runBlocking {
        item("local:abc")
        source("content://x/doc", "local:abc", SourceKind.LOCAL, available = false)
        assertEquals(ResolvedPlayback.Unavailable, resolver.resolve("local:abc"))
        assertEquals(ResolvedPlayback.Unavailable, resolver.resolve("local:no-sources"))
    }

    @Test
    fun prefersLocalCopyAndResumesFromLatestProgress() = runBlocking {
        item("d1")
        source("d1", "d1", SourceKind.DRIVE)
        source("content://x/d1", "d1", SourceKind.LOCAL)
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("d1", 5_000L, 100_000L, lastWatchedAt = 1L))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("content://x/d1", 9_000L, 100_000L, lastWatchedAt = 2L))

        assertEquals(
            ResolvedPlayback.Playable(PlaybackSource.Local("content://x/d1"), 9_000L),
            resolver.resolve("d1", requestedStartMs = 5_000L)
        )
    }

    @Test
    fun finishedTitleStartsFromTheBeginningAndRequestedStartUsedWithoutHistory() = runBlocking {
        item("d1")
        source("d1", "d1", SourceKind.DRIVE)
        assertEquals(ResolvedPlayback.Playable(PlaybackSource.Drive("d1"), 3_000L), resolver.resolve("d1", 3_000L))

        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("d1", 99_000L, 100_000L, isCompleted = true))
        assertEquals(ResolvedPlayback.Playable(PlaybackSource.Drive("d1"), 0L), resolver.resolve("d1", 3_000L))
    }
}
