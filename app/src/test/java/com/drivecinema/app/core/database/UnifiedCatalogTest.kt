package com.drivecinema.app.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.drivecinema.app.core.database.entity.MediaItemEntity
import com.drivecinema.app.core.database.entity.MediaSourceEntity
import com.drivecinema.app.core.database.entity.MediaType
import com.drivecinema.app.core.database.entity.PlaybackProgressEntity
import com.drivecinema.app.core.database.entity.SourceKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Task C.4 phase 1: sources, Drive-scoped library swaps and progress aggregated per title (model A). */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class UnifiedCatalogTest {

    private lateinit var db: DriveCinemaDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DriveCinemaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dao get() = db.mediaDao()
    private val sources get() = db.mediaSourceDao()

    private fun movie(id: String, title: String = id) =
        MediaItemEntity(id = id, type = MediaType.MOVIE, title = title, parentFolderId = "films")

    private suspend fun driveSwap(vararg ids: String, root: String = "rootA") =
        dao.replaceVisibleLibrary(ids.map { movie(it) }, emptyList(), emptyList(), libraryRootId = root)

    private suspend fun addLocalOnlyItem(itemId: String, contentUri: String) {
        dao.insertMediaItem(movie(itemId))
        dao.insertSourcesIfAbsent(listOf(MediaSourceEntity(contentUri, itemId, SourceKind.LOCAL, libraryRootId = "tree:movies")))
        dao.refreshVisibilityFromSources()
    }

    private suspend fun visibleMovieIds() = dao.getAllMoviesFlow().first().map { it.id }.sorted()

    @Test
    fun driveSwapRecordsDriveSourcesWithLibraryRoot() = runBlocking {
        driveSwap("d1", "d2")

        val source = sources.getSource("d1")
        assertNotNull(source)
        assertEquals("d1", source!!.mediaItemId)
        assertEquals(SourceKind.DRIVE, source.kind)
        assertEquals("rootA", source.libraryRootId)
        assertTrue(source.isAvailable)
        assertEquals(listOf("d1", "d2"), visibleMovieIds())
    }

    @Test
    fun driveLibrarySwitchNeverHidesLocalItems() = runBlocking {
        addLocalOnlyItem("local:abc", "content://tree/movies/Alpha.mkv")
        driveSwap("d1")

        driveSwap("d2", root = "rootB")

        assertEquals(listOf("d2", "local:abc"), visibleMovieIds())
        assertFalse(sources.getSource("d1")!!.isAvailable)
        assertNotNull("Unavailable sources are kept, never removed", sources.getSource("d1"))
    }

    @Test
    fun mergedItemStaysVisibleThroughItsLocalCopyWhenDriveCopyDisappears() = runBlocking {
        driveSwap("d1")
        dao.insertSourcesIfAbsent(listOf(MediaSourceEntity("content://tree/movies/D1.mkv", "d1", SourceKind.LOCAL)))

        driveSwap("d2")

        assertEquals(listOf("d1", "d2"), visibleMovieIds())
    }

    @Test
    fun driveSwapPreservesExistingSourceToItemLinks() = runBlocking {
        // d9 was previously merged into a local title.
        addLocalOnlyItem("local:xyz", "content://tree/movies/Gamma.mkv")
        dao.insertSourcesIfAbsent(listOf(MediaSourceEntity("d9", "local:xyz", SourceKind.DRIVE, isAvailable = false)))

        driveSwap("d9")

        val link = sources.getSource("d9")!!
        assertEquals("local:xyz", link.mediaItemId)
        assertTrue(link.isAvailable)
    }

    @Test
    fun continueWatchingAggregatesProgressAcrossSources() = runBlocking {
        driveSwap("d1")
        val localUri = "content://tree/movies/D1.mkv"
        dao.insertSourcesIfAbsent(listOf(MediaSourceEntity(localUri, "d1", SourceKind.LOCAL)))
        val progress = db.playbackProgressDao()
        progress.saveProgress(PlaybackProgressEntity("d1", positionMs = 100_000L, durationMs = 7_000_000L, lastWatchedAt = 1_000L))
        progress.saveProgress(PlaybackProgressEntity(localUri, positionMs = 300_000L, durationMs = 7_000_000L, lastWatchedAt = 2_000L))

        val continueWatching = dao.getContinueWatchingFlow().first()

        assertEquals(listOf("d1"), continueWatching.map { it.id })
        assertEquals(300_000L, continueWatching.single().positionMs)
        assertEquals(2_000L, continueWatching.single().lastWatchedAt)
        assertEquals(300_000L, sources.getLatestProgressForItem("d1")!!.positionMs)
        assertEquals(300_000L, sources.getLatestProgressForItemFlow("d1").first()!!.positionMs)
    }

    @Test
    fun continueWatchingExcludesItemWhoseLatestProgressIsCompleted() = runBlocking {
        driveSwap("d1")
        val localUri = "content://tree/movies/D1.mkv"
        dao.insertSourcesIfAbsent(listOf(MediaSourceEntity(localUri, "d1", SourceKind.LOCAL)))
        val progress = db.playbackProgressDao()
        progress.saveProgress(PlaybackProgressEntity("d1", positionMs = 100_000L, durationMs = 7_000_000L, lastWatchedAt = 1_000L))
        progress.saveProgress(
            PlaybackProgressEntity(localUri, positionMs = 6_900_000L, durationMs = 7_000_000L, isCompleted = true, lastWatchedAt = 2_000L)
        )

        assertTrue(dao.getContinueWatchingFlow().first().isEmpty())
    }

    @Test
    fun legacyProgressWithoutSourceRowStillShows() = runBlocking {
        // Items inserted directly (no source row) keep the pre-C.4 behaviour: progress keyed by item ID.
        dao.insertMediaItem(movie("m1"))
        db.playbackProgressDao().saveProgress(PlaybackProgressEntity("m1", positionMs = 50_000L, durationMs = 1_000_000L))

        assertEquals(listOf("m1"), dao.getContinueWatchingFlow().first().map { it.id })
    }

    @Test
    fun swapsNeverTouchWatchHistory() = runBlocking {
        driveSwap("d1")
        val progress = db.playbackProgressDao()
        progress.saveProgress(PlaybackProgressEntity("d1", positionMs = 42_000L, durationMs = 1_000_000L))
        progress.saveProgress(PlaybackProgressEntity("content://gone", positionMs = 7_000L, durationMs = 9_000L))

        driveSwap("d2", root = "rootB")
        driveSwap("d1")

        assertEquals(42_000L, progress.getProgress("d1")!!.positionMs)
        assertEquals(7_000L, progress.getProgress("content://gone")!!.positionMs)
    }
}
